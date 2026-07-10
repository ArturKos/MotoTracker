#!/usr/bin/env python3
"""
MotoTracker Android autonomous multi-agent implementation workflow.

A supervisor loop that drives six role-agents (each on its own model) through the
pipeline:

    architect -> programmer -> reviewer -> critic -> tester -> committer -> (loop)

Each role is a separate `claude -p` (headless) invocation with a role system
prompt and a per-role model. Control flow is driven by a machine-readable
`===VERDICT=== {json}` line every role must emit.

The supervisor's work ends when the architect reports `all_implemented` (or a
hard block / max iterations). State is checkpointed to `.workflow/state.json` so
the loop resumes exactly where it stopped — including after a token/usage limit,
which the supervisor waits out and then continues automatically.

Usage:
    python3 scripts/agent_workflow.py            # run the full loop (resumes)
    python3 scripts/agent_workflow.py --once     # run a single stage, then stop
    python3 scripts/agent_workflow.py --status    # print current status and exit
    python3 scripts/agent_workflow.py --reset     # clear state and start fresh
    python3 scripts/agent_workflow.py --max-iterations 5
    python3 scripts/agent_workflow.py --dry-run   # skip the committer (no push)
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path

# --------------------------------------------------------------------------- #
# Configuration
# --------------------------------------------------------------------------- #

REPO = Path(__file__).resolve().parent.parent
ROLES_DIR = REPO / "scripts" / "workflow_roles"
WF_DIR = REPO / ".workflow"
STATE_FILE = WF_DIR / "state.json"
STATUS_FILE = WF_DIR / "status"
PROGRESS_LOG = WF_DIR / "progress.log"
TRANSCRIPTS = WF_DIR / "transcripts"

# Model per role (decision: mix by purpose).
MODEL = {
    "architect": "claude-opus-4-8",
    "programmer": "claude-sonnet-4-6",
    "reviewer": "claude-sonnet-4-6",
    "critic": "claude-opus-4-8",
    "tester": "claude-haiku-4-5-20251001",
    "committer": "claude-haiku-4-5-20251001",
}

# Per-role wall-clock timeout (seconds) for a single agent invocation.
TIMEOUT = {
    "architect": 900,
    "programmer": 3600,
    "reviewer": 1200,
    "critic": 1200,
    "tester": 2400,
    "committer": 900,
}

CRITIC_PASS = 7          # critic score >= this passes
MAX_ATTEMPTS = 6         # per-task loop-backs before escalating / blocking
TOKEN_RETRY_BASE = 300   # seconds; backoff when reset time is unknown
TOKEN_RETRY_MAX = 1800

# Markers that indicate a usage/token/rate limit rather than a normal failure.
LIMIT_MARKERS = [
    "usage limit", "rate limit", "limit reached", "exceeded your usage",
    "/upgrade", "overloaded_error", "rate_limit_error",
    "too many requests", "credit balance",
    "session limit", "hit your", "you've hit", "resets",
]

# api_error_status values that mean "wait and retry" (rate / overloaded).
LIMIT_HTTP_STATUS = {429, 529}

PIPELINE_START = "architect"


# --------------------------------------------------------------------------- #
# Small utilities
# --------------------------------------------------------------------------- #

def now_iso() -> str:
    return dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def ensure_dirs() -> None:
    WF_DIR.mkdir(exist_ok=True)
    TRANSCRIPTS.mkdir(exist_ok=True)


def load_state() -> dict:
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text())
    return {
        "iteration": 1,
        "stage": PIPELINE_START,
        "task": None,            # {"task":..., "details":..., "files_hint":[...]}
        "attempts": 0,           # loop-backs for the current task
        "critic_score": None,
        "feedback": "",          # message passed back to programmer/architect
        "status": "running",     # running | waiting_tokens | all_done | blocked | error
        "history": [],           # list of {iter, stage, model, verdict, ts}
        "updated": now_iso(),
    }


def save_state(state: dict) -> None:
    state["updated"] = now_iso()
    STATE_FILE.write_text(json.dumps(state, indent=2, ensure_ascii=False))


def set_status(state: dict, line: str) -> None:
    """Update the live status line + append to the progress log + print."""
    stamp = now_iso()
    full = f"[{stamp}] iter {state['iteration']} | {line}"
    STATUS_FILE.write_text(full + "\n")
    with PROGRESS_LOG.open("a") as f:
        f.write(full + "\n")
    print(full, flush=True)


# --------------------------------------------------------------------------- #
# Verdict parsing & limit detection
# --------------------------------------------------------------------------- #

def parse_verdict(text: str) -> dict | None:
    """Extract the last `===VERDICT=== {json}` block from an agent's reply."""
    if not text:
        return None
    marker = "===VERDICT==="
    idx = text.rfind(marker)
    if idx < 0:
        return None
    tail = text[idx + len(marker):].strip()
    # The JSON may be inline or fenced; grab the first {...} balanced span.
    start = tail.find("{")
    if start < 0:
        return None
    depth = 0
    for i in range(start, len(tail)):
        if tail[i] == "{":
            depth += 1
        elif tail[i] == "}":
            depth -= 1
            if depth == 0:
                blob = tail[start:i + 1]
                try:
                    return json.loads(blob)
                except json.JSONDecodeError:
                    return None
    return None


def looks_like_limit(blob: str) -> bool:
    low = blob.lower()
    return any(m in low for m in LIMIT_MARKERS)


def extract_reset_epoch(blob: str) -> int | None:
    """Best-effort parse of a reset time from a limit message."""
    low = blob.lower()
    # Pattern: "...limit reached|1700000000" (Claude CLI style epoch).
    m = re.search(r"(?:limit reached|reset[^\d]{0,20})\|?(\d{10})", low)
    if m:
        return int(m.group(1))
    # Natural-language clock time, e.g. "resets 2am", "resets at 10:30pm".
    m = re.search(r"resets?\s*(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?", low)
    if m:
        hour = int(m.group(1))
        minute = int(m.group(2) or 0)
        ampm = m.group(3)
        if ampm == "pm" and hour != 12:
            hour += 12
        elif ampm == "am" and hour == 12:
            hour = 0
        if 0 <= hour <= 23:
            now = dt.datetime.now()
            target = now.replace(hour=hour, minute=minute, second=0, microsecond=0)
            if target <= now:                  # the time already passed today → tomorrow
                target += dt.timedelta(days=1)
            return int(target.timestamp())
    return None


# --------------------------------------------------------------------------- #
# Running a single agent
# --------------------------------------------------------------------------- #

def build_prompt(role: str, state: dict) -> str:
    """Compose the per-iteration user prompt handed to the role agent."""
    task = state.get("task") or {}
    fb = state.get("feedback", "")
    parts = [
        f"You are the {role.upper()} agent. Iteration {state['iteration']}, "
        f"attempt {state['attempts']} for this task.",
        "Work in the MotoTracker Android app project (current directory). The "
        "binding design spec is README.md (the design handoff) plus the "
        "MotoTracker*.dc.html prototypes and screenshots/. Read BACKLOG.md, "
        "CLAUDE.md and `git log --oneline -15` for context as needed.",
    ]
    if role == "architect":
        if fb:
            parts.append(f"NOTE from the loop (a previous attempt was sent back): {fb}")
        parts.append("Determine the single next feature to implement and emit the VERDICT.")
    else:
        parts.append("CURRENT TASK (from the architect):")
        parts.append(json.dumps(task, ensure_ascii=False, indent=2))
        if fb:
            parts.append("FEEDBACK you MUST address from the previous stage:")
            parts.append(fb)
    parts.append(
        "CRITICAL: end your reply with EXACTLY ONE line "
        "`===VERDICT=== {json}` — always, no matter what. Never wait or poll for "
        "long-running EXTERNAL jobs (e.g. a remote GitHub Actions run that takes "
        "many minutes); do your local work, report your best current assessment in "
        "the verdict, and let the supervisor decide. Do not stall waiting for async "
        "results — a reply without a VERDICT line is a failure.")
    return "\n\n".join(parts)


def run_agent(role: str, state: dict) -> dict:
    """
    Invoke a role agent. Returns:
      {"ok":bool, "verdict":dict|None, "result":str, "limit":bool, "reset":int|None}
    """
    role_file = ROLES_DIR / f"{role}.md"
    model = MODEL[role]
    prompt = build_prompt(role, state)

    cmd = [
        "claude", "-p", prompt,
        "--model", model,
        "--append-system-prompt-file", str(role_file),
        "--permission-mode", "bypassPermissions",
        "--output-format", "json",
    ]
    # Launch in its OWN process group (start_new_session) so that on timeout we
    # can kill the whole tree. Otherwise the agent's child builds (Gradle
    # daemons, Kotlin/test JVMs) get orphaned when the agent is killed, hang on
    # the daemon lock, and pile up as dozens of stray `java` processes.
    proc = subprocess.Popen(
        cmd, cwd=str(REPO), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, start_new_session=True,
    )
    try:
        out, err = proc.communicate(timeout=TIMEOUT[role])
        rc = proc.returncode
    except subprocess.TimeoutExpired:
        try:
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
        except (ProcessLookupError, PermissionError):
            pass
        try:
            out, err = proc.communicate(timeout=30)
        except subprocess.TimeoutExpired:
            out, err = "", ""
        return {"ok": False, "verdict": None, "result": "",
                "limit": False, "reset": None, "error": "timeout"}

    raw = (out or "") + "\n" + (err or "")
    # Persist transcript for debugging.
    tpath = TRANSCRIPTS / f"{state['iteration']:03d}-{role}-{state['attempts']}.txt"
    tpath.write_text(raw)

    result_text = ""
    is_error = rc != 0
    api_status = None
    try:
        data = json.loads(out)
        result_text = data.get("result", "") or ""
        is_error = is_error or bool(data.get("is_error"))
        api_status = data.get("api_error_status")
    except (json.JSONDecodeError, TypeError):
        result_text = out or ""

    combined = raw + "\n" + result_text
    # A usage/session/rate limit: the HTTP status is the most reliable signal,
    # with a text-marker fallback for messages the CLI surfaces without a code.
    if api_status in LIMIT_HTTP_STATUS or \
       ((is_error or rc != 0) and looks_like_limit(combined)):
        return {"ok": False, "verdict": None, "result": result_text,
                "limit": True, "reset": extract_reset_epoch(combined)}

    verdict = parse_verdict(result_text)
    return {"ok": (verdict is not None) and not is_error,
            "verdict": verdict, "result": result_text,
            "limit": False, "reset": None,
            "error": None if verdict else "no_verdict"}


# --------------------------------------------------------------------------- #
# Token / usage-limit handling
# --------------------------------------------------------------------------- #

def wait_for_tokens(state: dict, reset_epoch: int | None, backoff: int) -> int:
    """Sleep until the usage limit should be lifted; return next backoff."""
    state["status"] = "waiting_tokens"
    save_state(state)
    if reset_epoch:
        wait = max(60, reset_epoch - int(time.time()) + 60)
        until = dt.datetime.fromtimestamp(reset_epoch).strftime("%H:%M:%S")
        set_status(state, f"WAITING for token reset (~{until}); sleeping {wait}s")
    else:
        wait = backoff
        set_status(state, f"WAITING for token reset (unknown); sleeping {wait}s then retry")
    # Sleep in chunks so a tail -f of the status file shows we're alive.
    slept = 0
    while slept < wait:
        chunk = min(60, wait - slept)
        time.sleep(chunk)
        slept += chunk
    state["status"] = "running"
    save_state(state)
    return min(TOKEN_RETRY_MAX, backoff * 2)


# --------------------------------------------------------------------------- #
# Stage transitions
# --------------------------------------------------------------------------- #

def record(state: dict, role: str, verdict: dict) -> None:
    state["history"].append({
        "iter": state["iteration"], "stage": role, "model": MODEL[role],
        "verdict": verdict, "ts": now_iso(),
    })
    state["history"] = state["history"][-200:]  # cap


def advance(state: dict, role: str, verdict: dict, dry_run: bool) -> None:
    """Mutate `state` to the next stage based on the agent's verdict."""
    record(state, role, verdict)
    st = (verdict.get("status") or "").lower()

    if role == "architect":
        if st == "all_implemented":
            state["status"] = "all_done"
            return
        state["task"] = {k: verdict.get(k) for k in ("task", "details", "files_hint")}
        state["attempts"] = 1
        state["feedback"] = ""
        state["critic_score"] = None
        state["stage"] = "programmer"

    elif role == "programmer":
        if st == "blocked":
            state["feedback"] = f"Programmer blocked: {verdict.get('notes', '')}"
            _bounce(state, "architect")
        else:
            state["feedback"] = ""
            state["stage"] = "reviewer"

    elif role == "reviewer":
        if st == "approved":
            state["feedback"] = ""
            state["stage"] = "critic"
        else:
            state["feedback"] = "Reviewer requires: " + "; ".join(verdict.get("issues", []))
            _bounce(state, "programmer")

    elif role == "critic":
        score = int(verdict.get("score", 0))
        state["critic_score"] = score
        if st == "pass" and score >= CRITIC_PASS:
            state["feedback"] = ""
            state["stage"] = "tester"
        else:
            target = verdict.get("return_to") or "programmer"
            state["feedback"] = f"Critic score {score}/9 — {verdict.get('feedback', '')}"
            _bounce(state, target if target in ("programmer", "architect") else "programmer")

    elif role == "tester":
        if st == "pass":
            state["feedback"] = ""
            state["stage"] = "committer" if not dry_run else "_done_iter"
            if dry_run:
                set_status(state, "DRY-RUN: tests passed; skipping committer")
                _next_iteration(state)
        else:
            state["feedback"] = "Tester failed: " + (verdict.get("log_tail", "") or verdict.get("summary", ""))
            _bounce(state, "programmer")

    elif role == "committer":
        if st in ("committed", "skipped"):
            _next_iteration(state)
        else:
            state["status"] = "error"
            state["feedback"] = f"Committer error: {verdict.get('summary', '')}"


def _bounce(state: dict, target: str) -> None:
    """Send the task back to programmer/architect, guarding against infinite loops."""
    state["attempts"] += 1
    if state["attempts"] > MAX_ATTEMPTS:
        if target == "programmer":
            # Escalate to the architect once before giving up.
            state["feedback"] = ("Too many failed attempts at implementation. "
                                 "Reconsider the design/spec. " + state["feedback"])
            state["stage"] = "architect"
            state["attempts"] = 1
        else:
            state["status"] = "blocked"
    else:
        state["stage"] = target


def _next_iteration(state: dict) -> None:
    state["iteration"] += 1
    state["stage"] = "architect"
    state["task"] = None
    state["attempts"] = 0
    state["critic_score"] = None
    state["feedback"] = ""


# --------------------------------------------------------------------------- #
# Main loop
# --------------------------------------------------------------------------- #

def short_task(state: dict) -> str:
    t = state.get("task") or {}
    return (t.get("task") or "(deciding next task)")[:70]


def run_loop(once: bool, dry_run: bool, max_iterations: int | None) -> int:
    ensure_dirs()
    state = load_state()
    if state["status"] in ("all_done", "blocked", "error"):
        set_status(state, f"Workflow already in terminal state: {state['status']}. "
                          f"Use --reset to start over.")
        return 0

    backoff = TOKEN_RETRY_BASE
    start_iter = state["iteration"]

    while True:
        if max_iterations and (state["iteration"] - start_iter) >= max_iterations:
            set_status(state, f"Reached --max-iterations ({max_iterations}); stopping.")
            return 0

        stage = state["stage"]
        if stage == "_done_iter":          # dry-run bookkeeping sentinel
            stage = state["stage"] = "architect"

        model = MODEL.get(stage, "?")
        set_status(state, f"{stage.upper()} ({model}) attempt {state['attempts']} "
                          f"— {short_task(state)}")
        save_state(state)

        res = run_agent(stage, state)

        if res["limit"]:
            backoff = wait_for_tokens(state, res["reset"], backoff)
            continue  # re-run the SAME stage after the reset

        backoff = TOKEN_RETRY_BASE  # healthy call resets the backoff

        if not res["ok"] or res["verdict"] is None:
            err = res.get("error", "unknown")
            # Retry a few times; a limit mid-retry is handled by waiting.
            for _ in range(2):
                set_status(state, f"{stage.upper()} produced no usable VERDICT ({err}); retrying")
                res = run_agent(stage, state)
                if res["limit"]:
                    backoff = wait_for_tokens(state, res["reset"], backoff)
                    res = run_agent(stage, state)
                if res["ok"] and res["verdict"] is not None:
                    break
            if not res["ok"] or res["verdict"] is None:
                # Keep the loop alive instead of hard-stopping: bounce the work
                # back (bounded by MAX_ATTEMPTS) with a corrective note. Only the
                # architect (the planner) is treated as fatal if it can't speak.
                if stage == "architect":
                    state["status"] = "error"
                    state["feedback"] = "architect produced no VERDICT."
                    save_state(state)
                    set_status(state, "ERROR: architect gave no VERDICT. See transcripts/. Stopping.")
                    return 1
                state["feedback"] = (f"The previous {stage} reply had NO VERDICT line. "
                                     "Redo the work and end with exactly one "
                                     "`===VERDICT=== {json}` line; never wait on async CI.")
                set_status(state, f"{stage.upper()} gave no VERDICT after retries; bouncing to programmer")
                _bounce(state, "programmer")
                save_state(state)
                continue

        verdict = res["verdict"]
        set_status(state, f"{stage.upper()} verdict: {json.dumps(verdict, ensure_ascii=False)[:200]}")
        advance(state, stage, verdict, dry_run)
        save_state(state)

        if state["status"] == "all_done":
            set_status(state, "ALL IMPLEMENTED — architect reports nothing left. "
                              "Supervisor done; please review.")
            return 0
        if state["status"] == "blocked":
            set_status(state, f"BLOCKED after {MAX_ATTEMPTS} attempts on: {short_task(state)}. "
                              "Human intervention needed.")
            return 2
        if state["status"] == "error":
            set_status(state, f"ERROR: {state['feedback']}. Stopping.")
            return 1

        if once and stage == "committer":
            return 0
        if once:
            set_status(state, "--once: completed one stage; stopping.")
            return 0


# --------------------------------------------------------------------------- #
# Entry point
# --------------------------------------------------------------------------- #

def main() -> int:
    ap = argparse.ArgumentParser(description="MotoTracker Android autonomous agent workflow")
    ap.add_argument("--once", action="store_true", help="run a single stage then stop")
    ap.add_argument("--status", action="store_true", help="print current status and exit")
    ap.add_argument("--reset", action="store_true", help="clear state and start fresh")
    ap.add_argument("--dry-run", action="store_true", help="skip the committer (no commit/push)")
    ap.add_argument("--max-iterations", type=int, default=None)
    args = ap.parse_args()

    ensure_dirs()

    if args.status:
        if STATUS_FILE.exists():
            print(STATUS_FILE.read_text().strip())
        if STATE_FILE.exists():
            s = load_state()
            print(f"stage={s['stage']} iter={s['iteration']} attempts={s['attempts']} "
                  f"status={s['status']} score={s['critic_score']}")
            print(f"task={short_task(s)}")
        return 0

    if args.reset:
        for p in (STATE_FILE, STATUS_FILE):
            if p.exists():
                p.unlink()
        print("Workflow state reset.")
        return 0

    return run_loop(once=args.once, dry_run=args.dry_run,
                    max_iterations=args.max_iterations)


if __name__ == "__main__":
    sys.exit(main())

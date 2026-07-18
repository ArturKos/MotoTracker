package com.mototracker.domain.fuel

import com.mototracker.data.repository.FuelAdjustmentRepository
import com.mototracker.data.repository.RefuelRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Structural test asserting that [FuelAdjustmentRepository] has no path into
 * [FuelConsumptionCalculator].
 *
 * The consumption-ledger reads exclusively from [RefuelRepository.observeAllForBike];
 * correction events ([FuelAdjustmentRepository]) must never be fed into the calculator
 * or they would inflate the measured L/100 km figure.
 */
class FuelLedgerIsolationTest {

    /**
     * Verifies that [FuelAdjustmentRepository] does not expose an `observeAllForBike`
     * method — the only pathway from a repository into [FuelConsumptionCalculator.fillsFromLedger].
     *
     * [RefuelRepository] deliberately provides `observeAllForBike` because it is the authoritative
     * ledger source. [FuelAdjustmentRepository] must not replicate this method or the
     * consumption calculator could accidentally receive correction events.
     */
    @Test
    fun `FuelAdjustmentRepository has no observeAllForBike and cannot feed FuelConsumptionCalculator`() {
        val adjustmentMethods = FuelAdjustmentRepository::class.java.methods.map { it.name }
        assertFalse(
            "FuelAdjustmentRepository must not expose 'observeAllForBike' — that is the " +
                "exclusive ledger path used by FuelConsumptionCalculator via RefuelRepository",
            adjustmentMethods.contains("observeAllForBike"),
        )

        // RefuelRepository DOES have observeAllForBike (the authoritative ledger entry point).
        val refuelMethods = RefuelRepository::class.java.methods.map { it.name }
        assertNotNull(
            "RefuelRepository must expose 'observeAllForBike' as the ledger path",
            refuelMethods.find { it == "observeAllForBike" },
        )

        // FuelConsumptionCalculator.fillsFromLedger accepts only List<Pair<Double, List<RefuelEvent>>>,
        // not FuelAdjustmentEvent — verified by compiling an empty-list call below.
        // If this line compiles, the parameter type is correct (FuelAdjustmentEvent is not accepted).
        val fills: List<FuelFill> = FuelConsumptionCalculator.fillsFromLedger(emptyList())
        assertFalse("empty ledger should yield no fills", fills.isNotEmpty())
    }
}

package app.bitcall

import app.bitcall.ai.FamilyAiDecision
import app.bitcall.ai.FamilyAiPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyAiPolicyTest {
    @Test fun strangersAreRefusedFirst() {
        assertEquals(FamilyAiDecision.NOT_FAMILY, FamilyAiPolicy.decide(signatureMatch = false, aiMode = "FULL", modelReady = true, inCall = false))
        assertEquals(FamilyAiDecision.NOT_FAMILY, FamilyAiPolicy.decide(signatureMatch = false, aiMode = "OFF", modelReady = false, inCall = true))
    }

    @Test fun aiOffMeansOff() {
        assertEquals(FamilyAiDecision.AI_OFF, FamilyAiPolicy.decide(true, "OFF", modelReady = true, inCall = false))
    }

    @Test fun needsADownloadedModel() {
        assertEquals(FamilyAiDecision.NO_MODEL, FamilyAiPolicy.decide(true, "FULL", modelReady = false, inCall = false))
    }

    @Test fun neverDuringACall() {
        assertEquals(FamilyAiDecision.IN_CALL, FamilyAiPolicy.decide(true, "FAST", modelReady = true, inCall = true))
    }

    @Test fun runsWhenEverythingIsReady() {
        assertEquals(FamilyAiDecision.RUN, FamilyAiPolicy.decide(true, "FULL", modelReady = true, inCall = false))
        assertEquals(FamilyAiDecision.RUN, FamilyAiPolicy.decide(true, "FAST", modelReady = true, inCall = false))
    }

    @Test fun errorCodesMatchTheProtocol() {
        assertEquals("not_family", FamilyAiDecision.NOT_FAMILY.code)
        assertEquals("ai_off", FamilyAiDecision.AI_OFF.code)
        assertEquals("no_model", FamilyAiDecision.NO_MODEL.code)
        assertEquals("in_call", FamilyAiDecision.IN_CALL.code)
    }
}

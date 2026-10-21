package app.bitcall.ai

/** Whether a request from another 128bit app may use bitcall's on-device model, and if not, why. */
enum class FamilyAiDecision(val code: String) {
    RUN("ok"),
    NOT_FAMILY("not_family"),
    AI_OFF("ai_off"),
    NO_MODEL("no_model"),
    IN_CALL("in_call"),
}

object FamilyAiPolicy {
    /** Strangers are refused before anything else is revealed; calls always win over AI work. */
    fun decide(signatureMatch: Boolean, aiMode: String, modelReady: Boolean, inCall: Boolean): FamilyAiDecision = when {
        !signatureMatch -> FamilyAiDecision.NOT_FAMILY
        aiMode == "OFF" -> FamilyAiDecision.AI_OFF
        !modelReady -> FamilyAiDecision.NO_MODEL
        inCall -> FamilyAiDecision.IN_CALL
        else -> FamilyAiDecision.RUN
    }
}

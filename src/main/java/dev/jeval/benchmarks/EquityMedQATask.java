package dev.jeval.benchmarks;

public enum EquityMedQATask {
    EHAI("ehai"),
    FBRT_LLM("fbrt_llm"),
    FBRT_LLM_661_SAMPLED("fbrt_llm_661_sampled"),
    FBRT_MANUAL("fbrt_manual"),
    MIXED_MMQA_OMAQ("mixed_mmqa_omaq"),
    MULTIMEDQA("multimedqa"),
    OMAQ("omaq"),
    OMIYE_ET_AL("omiye_et_al"),
    TRINDS("trinds");

    private final String value;

    EquityMedQATask(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}

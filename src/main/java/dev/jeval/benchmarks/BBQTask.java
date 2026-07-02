package dev.jeval.benchmarks;

public enum BBQTask {
    AGE("Age"),
    DISABILITY_STATUS("Disability_status"),
    GENDER_IDENTITY("Gender_identity"),
    NATIONALITY("Nationality"),
    PHYSICAL_APPEARANCE("Physical_appearance"),
    RACE_ETHNICITY("Race_ethnicity"),
    RACE_X_SES("Race_x_SES"),
    RACE_X_GENDER("Race_x_gender"),
    RELIGION("Religion"),
    SES("SES"),
    SEXUAL_ORIENTATION("Sexual_orientation");

    private final String value;

    BBQTask(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}

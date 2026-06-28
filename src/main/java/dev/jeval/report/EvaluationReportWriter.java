package dev.jeval.report;

import dev.jeval.MetricResult;
import dev.jeval.runner.TestRunResult;
import java.util.Locale;

public final class EvaluationReportWriter {
    private EvaluationReportWriter() {
    }

    public static String markdown(TestRunResult result) {
        var report = new StringBuilder();
        report.append("# JEval Evaluation Results\n\n");
        report.append("Evaluation: ").append(result.name()).append("\n\n");
        report.append("Summary: total=").append(result.summary().total())
                .append(" passed=").append(result.summary().passed())
                .append(" failed=").append(result.summary().failed())
                .append(" average=").append(number(result.summary().averageScore()))
                .append(" passRate=").append(percent(result.summary().passRate()))
                .append("\n\n");
        report.append("## Aggregate Metrics\n\n");
        report.append("| Metric | Average Score | Pass Rate | Total |\n");
        report.append("| --- | ---: | ---: | ---: |\n");
        for (var aggregate : result.aggregates()) {
            report.append("| ").append(aggregate.name())
                    .append(" | ").append(number(aggregate.averageScore()))
                    .append(" | ").append(percent(aggregate.passRate()))
                    .append(" | ").append(aggregate.total())
                    .append(" |\n");
        }
        report.append("\n## Test Cases\n\n");
        for (var testCase : result.results()) {
            report.append("### ").append(testCase.name()).append("\n\n");
            for (MetricResult metric : testCase.metricResults()) {
                report.append("- ").append(metric.name())
                        .append(": ").append(metric.success() ? "passed" : "failed")
                        .append(" (score ").append(number(metric.score())).append(") ")
                        .append(metric.reason()).append("\n");
            }
            report.append("\n");
        }
        return report.toString();
    }

    public static String html(TestRunResult result) {
        return "<!doctype html><html><body>"
                + "<h1>JEval Evaluation Results</h1>"
                + "<p>Evaluation: " + escape(result.name()) + "</p>"
                + "<h2>Aggregate Metrics</h2>"
                + "<table><tr><th>Metric</th><th>Average Score</th><th>Pass Rate</th><th>Total</th></tr>"
                + aggregatesHtml(result)
                + "</table></body></html>";
    }

    private static String aggregatesHtml(TestRunResult result) {
        var rows = new StringBuilder();
        for (var aggregate : result.aggregates()) {
            rows.append("<tr><td>").append(escape(aggregate.name()))
                    .append("</td><td>").append(number(aggregate.averageScore()))
                    .append("</td><td>").append(percent(aggregate.passRate()))
                    .append("</td><td>").append(aggregate.total())
                    .append("</td></tr>");
        }
        return rows.toString();
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

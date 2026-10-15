package sk.glorfeed.researchservice.research.services;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import sk.glorfeed.researchservice.ResearchServiceApplication;

import sk.glorfeed.researchservice.outbox.domain.OutboxEvent;
import sk.glorfeed.researchservice.outbox.repositories.OutboxEventRepository;
import sk.glorfeed.researchservice.research.domain.ResearchCitation;
import sk.glorfeed.researchservice.research.domain.ResearchReport;
import sk.glorfeed.researchservice.research.domain.ResearchRun;
import sk.glorfeed.researchservice.research.domain.ResearchSource;
import sk.glorfeed.researchservice.research.domain.ResearchSubject;
import sk.glorfeed.researchservice.research.repositories.ResearchRunRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ResearchServiceApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:research-recorder-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=true",
    "RESEARCH_DB_URL=jdbc:h2:mem:research-recorder-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "RESEARCH_DB_USER=sa",
    "RESEARCH_DB_PASSWORD=",
    "spring.kafka.listener.auto-startup=false",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ResearchRunRecorderTransactionTest {

    @Autowired
    private ResearchRunRecorder recorder;

    @Autowired
    private ResearchRunRepository runRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reportAndOutboxCommitTogetherAndRollbackTogether() {
        ResearchSubject subject = new ResearchSubject(UUID.randomUUID(), "Climate", "Policy research");
        ResearchReport report = new ResearchReport(
                subject.topicId(),
                "resp-success",
                "gpt-6-luna",
                "A cited report",
                List.of(new ResearchCitation("Source", "https://example.test/source")),
                List.of(new ResearchSource("Source", "https://example.test/source")),
                20,
                30,
                1);

        UUID runId = recorder.record(subject, report);

        assertThat(runRepository.findById(runId)).get()
                .extracting(ResearchRun::getTopicId)
                .isEqualTo(subject.topicId());
        assertThat(outboxRepository.count()).isEqualTo(1);

        outboxRepository.deleteAll();
        runRepository.deleteAll();
        jdbcTemplate.execute("ALTER TABLE outbox_event ADD CONSTRAINT reject_research_completed CHECK (event_type <> 'ResearchCompleted')");

        ResearchReport rejectedReport = new ResearchReport(
                subject.topicId(), "resp-rejected", "gpt-6-luna", "Report", List.of(), List.of(), 1, 1, 1);
        assertThatThrownBy(() -> recorder.record(subject, rejectedReport)).isInstanceOf(RuntimeException.class);

        assertThat(runRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }
}

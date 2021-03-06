package com.rbkmoney.fraudbusters.repository;

import com.rbkmoney.damsel.geo_ip.GeoIpServiceSrv;
import com.rbkmoney.fraudbusters.config.ClickhouseConfig;
import com.rbkmoney.fraudbusters.constant.EventP2PField;
import com.rbkmoney.fraudbusters.constant.ScoresType;
import com.rbkmoney.fraudbusters.converter.ScoresResultToEventConverter;
import com.rbkmoney.fraudbusters.converter.ScoresResultToEventP2PConverter;
import com.rbkmoney.fraudbusters.domain.CheckedResultModel;
import com.rbkmoney.fraudbusters.domain.ScoresResult;
import com.rbkmoney.fraudbusters.fraud.constant.P2PCheckedField;
import com.rbkmoney.fraudbusters.fraud.model.FieldModel;
import com.rbkmoney.fraudbusters.fraud.model.P2PModel;
import com.rbkmoney.fraudbusters.fraud.p2p.resolver.DbP2pFieldResolver;
import com.rbkmoney.fraudbusters.repository.impl.AggregationGeneralRepositoryImpl;
import com.rbkmoney.fraudbusters.repository.impl.p2p.EventP2PRepository;
import com.rbkmoney.fraudbusters.util.BeanUtil;
import com.rbkmoney.fraudbusters.util.ChInitializer;
import com.rbkmoney.fraudbusters.util.TimestampUtil;
import com.rbkmoney.fraudo.constant.ResultStatus;
import com.rbkmoney.fraudo.model.ResultModel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.ClickHouseContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.rbkmoney.fraudbusters.util.ChInitializer.execAllInFile;
import static org.junit.Assert.assertEquals;

@Slf4j
@RunWith(SpringRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = {EventP2PRepository.class, ScoresResultToEventConverter.class,
        ScoresResultToEventP2PConverter.class, ClickhouseConfig.class, DbP2pFieldResolver.class, AggregationGeneralRepositoryImpl.class},
        initializers = EventP2PRepositoryTest.Initializer.class)
public class EventP2PRepositoryTest {

    private static final String SELECT_COUNT_AS_CNT_FROM_FRAUD_EVENTS_UNIQUE = "SELECT count() as cnt from fraud.events_p_to_p";

    @ClassRule
    public static ClickHouseContainer clickHouseContainer = new ClickHouseContainer("yandex/clickhouse-server:19.17");

    @Autowired
    private EventP2PRepository eventP2PRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    ScoresResultToEventConverter scoresResultToEventConverter;

    @Autowired
    DbP2pFieldResolver dbP2pFieldResolver;

    @MockBean
    GeoIpServiceSrv.Iface iface;

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @SneakyThrows
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            log.info("clickhouse.db.url={}", clickHouseContainer.getJdbcUrl());
            TestPropertyValues
                    .of("clickhouse.db.url=" + clickHouseContainer.getJdbcUrl(),
                            "clickhouse.db.user=" + clickHouseContainer.getUsername(),
                            "clickhouse.db.password=" + clickHouseContainer.getPassword())
                    .applyTo(configurableApplicationContext.getEnvironment());

            initDb();
        }
    }

    private static void initDb() throws SQLException {
        try (Connection connection = ChInitializer.getSystemConn(clickHouseContainer)) {
            execAllInFile(connection, "sql/db_init.sql");
            execAllInFile(connection, "sql/V2__create_events_p2p.sql");
        }
    }

    @Before
    public void setUp() throws Exception {
        initDb();
    }

    @Test
    public void insert() throws SQLException {
        eventP2PRepository.insert(scoresResultToEventConverter
                .convert(createScoresResult(ResultStatus.ACCEPT, BeanUtil.createP2PModel()))
        );

        Integer count = jdbcTemplate.queryForObject(SELECT_COUNT_AS_CNT_FROM_FRAUD_EVENTS_UNIQUE,
                (resultSet, i) -> resultSet.getInt("cnt"));

        assertEquals(1, count.intValue());
    }

    @Test
    public void insertBatch() throws SQLException {
        eventP2PRepository.insertBatch(
                createBatch().stream()
                        .map(scoresResultToEventConverter::convert)
                        .collect(Collectors.toList())
        );

        Integer count = jdbcTemplate.queryForObject(SELECT_COUNT_AS_CNT_FROM_FRAUD_EVENTS_UNIQUE,
                (resultSet, i) -> resultSet.getInt("cnt"));

        assertEquals(2, count.intValue());
    }

    @NotNull
    private List<ScoresResult<P2PModel>> createBatch() {
        ScoresResult<P2PModel> value = createScoresResult(ResultStatus.ACCEPT, BeanUtil.createP2PModel());
        ScoresResult<P2PModel> value2 = createScoresResult(ResultStatus.DECLINE, BeanUtil.createP2PModelSecond());
        return List.of(value, value2);
    }

    @NotNull
    private ScoresResult<P2PModel> createScoresResult(ResultStatus status, P2PModel p2PModel) {
        ScoresResult<P2PModel> value2 = new ScoresResult<>();
        CheckedResultModel resultModel = new CheckedResultModel();
        resultModel.setResultModel(new ResultModel(status, "test", null));
        resultModel.setCheckedTemplate("RULE");
        value2.setRequest(p2PModel);
        HashMap<String, CheckedResultModel> map = new HashMap<>();
        map.put(ScoresType.FRAUD, resultModel);
        value2.setScores(map);
        return value2;
    }

    @Test
    public void countOperationByEmailTest() throws SQLException {
        eventP2PRepository.insertBatch(
                createBatch().stream()
                        .map(scoresResultToEventConverter::convert)
                        .collect(Collectors.toList())
        );

        Instant now = Instant.now();
        Long to = TimestampUtil.generateTimestampNowMillis(now);
        Long from = TimestampUtil.generateTimestampMinusMinutesMillis(now, 10L);

        List<Map<String, Object>> maps = jdbcTemplate.queryForList("SELECT * from fraud.events_p_to_p");
        maps.forEach(stringObjectMap -> System.out.println(stringObjectMap));

        int count = eventP2PRepository.countOperationByField(EventP2PField.email.name(), BeanUtil.EMAIL, from, to);
        assertEquals(1, count);
    }

    @Test
    public void countOperationByEmailTestWithGroupBy() throws SQLException {
        P2PModel p2PModelSecond = BeanUtil.createP2PModelSecond();
        p2PModelSecond.setIdentityId("test");
        eventP2PRepository.insertBatch(scoresResultToEventConverter
                .convertBatch(List.of(
                        createScoresResult(ResultStatus.ACCEPT, BeanUtil.createP2PModel()),
                        createScoresResult(ResultStatus.DECLINE, BeanUtil.createP2PModelSecond()),
                        createScoresResult(ResultStatus.DECLINE, p2PModelSecond))
                )
        );

        Instant now = Instant.now();
        Long to = TimestampUtil.generateTimestampNowMillis(now);
        Long from = TimestampUtil.generateTimestampMinusMinutesMillis(now, 10L);

        FieldModel email = dbP2pFieldResolver.resolve(P2PCheckedField.EMAIL, p2PModelSecond);
        int count = eventP2PRepository.countOperationByFieldWithGroupBy(EventP2PField.email.name(), email.getValue(), from, to, List.of());
        assertEquals(2, count);

        FieldModel resolve = dbP2pFieldResolver.resolve(P2PCheckedField.IDENTITY_ID, p2PModelSecond);
        count = eventP2PRepository.countOperationByFieldWithGroupBy(EventP2PField.email.name(), email.getValue(), from, to, List.of(resolve));
        assertEquals(1, count);
    }

    @Test
    public void sumOperationByEmailTest() throws SQLException {
        eventP2PRepository.insertBatch(scoresResultToEventConverter.convertBatch(createBatch()));

        Instant now = Instant.now();
        Long to = TimestampUtil.generateTimestampNowMillis(now);
        Long from = TimestampUtil.generateTimestampMinusMinutesMillis(now, 10L);

        Long sum = eventP2PRepository.sumOperationByFieldWithGroupBy(EventP2PField.email.name(), BeanUtil.EMAIL, from, to, List.of());
        assertEquals(BeanUtil.AMOUNT_FIRST, sum);
    }

    @Test
    public void countUniqOperationTest() {
        P2PModel p2pModel = BeanUtil.createP2PModel();
        p2pModel.setFingerprint("test");
        eventP2PRepository.insertBatch(scoresResultToEventConverter
                .convertBatch(List.of(
                        createScoresResult(ResultStatus.ACCEPT, BeanUtil.createP2PModel()),
                        createScoresResult(ResultStatus.DECLINE, BeanUtil.createP2PModelSecond()),
                        createScoresResult(ResultStatus.DECLINE, BeanUtil.createP2PModel()),
                        createScoresResult(ResultStatus.DECLINE, p2pModel))
                )
        );

        Instant now = Instant.now();
        Long to = TimestampUtil.generateTimestampNowMillis(now);
        Long from = TimestampUtil.generateTimestampMinusMinutesMillis(now, 10L);

        Integer sum = eventP2PRepository.uniqCountOperation(EventP2PField.email.name(), BeanUtil.EMAIL, EventP2PField.fingerprint.name(), from, to);
        assertEquals(Integer.valueOf(2), sum);
    }

    @Test
    public void countUniqOperationWithGroupByTest() {
        P2PModel p2pModel = BeanUtil.createP2PModel();
        p2pModel.setFingerprint("test");
        p2pModel.setIdentityId("identity");

        eventP2PRepository.insertBatch(scoresResultToEventConverter
                .convertBatch(List.of(
                        createScoresResult(ResultStatus.ACCEPT, BeanUtil.createP2PModel()),
                        createScoresResult(ResultStatus.DECLINE, BeanUtil.createP2PModelSecond()),
                        createScoresResult(ResultStatus.DECLINE, BeanUtil.createP2PModel()),
                        createScoresResult(ResultStatus.DECLINE, p2pModel))
                )
        );

        Instant now = Instant.now();
        Long to = TimestampUtil.generateTimestampNowMillis(now);
        Long from = TimestampUtil.generateTimestampMinusMinutesMillis(now, 10L);
        Integer sum = eventP2PRepository.uniqCountOperationWithGroupBy(EventP2PField.email.name(), BeanUtil.EMAIL, EventP2PField.fingerprint.name(), from, to, List.of());
        assertEquals(Integer.valueOf(2), sum);

        FieldModel resolve = dbP2pFieldResolver.resolve(P2PCheckedField.IDENTITY_ID, p2pModel);
        sum = eventP2PRepository.uniqCountOperationWithGroupBy(EventP2PField.email.name(), BeanUtil.EMAIL, EventP2PField.fingerprint.name(), from, to, List.of(resolve));
        assertEquals(Integer.valueOf(1), sum);
    }

}
package com.epam.reportportal.base.core.tms.sync.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epam.reportportal.base.core.events.domain.tms.TestCaseCreatedEvent;
import com.epam.reportportal.base.core.events.domain.tms.TestCaseFieldChangedEvent;
import com.epam.reportportal.base.core.tms.dto.TmsManualScenarioRQ;
import com.epam.reportportal.base.core.tms.dto.TmsTextManualScenarioRQ;
import com.epam.reportportal.base.core.tms.mapper.TmsAttachmentMapper;
import com.epam.reportportal.base.core.tms.mapper.TmsManualScenarioMapper;
import com.epam.reportportal.base.core.tms.mapper.TmsTestCaseActivityResourceMapper;
import com.epam.reportportal.base.core.tms.mapper.TmsTestCaseMapper;
import com.epam.reportportal.base.core.tms.service.TmsTestCaseAttributeService;
import com.epam.reportportal.base.core.tms.service.TmsTestCaseVersionService;
import com.epam.reportportal.base.core.tms.sync.TmsSyncConnector;
import com.epam.reportportal.base.core.tms.sync.dto.RemoteTestCase;
import com.epam.reportportal.base.infrastructure.persistence.binary.tms.TmsAttachmentDataStoreService;
import com.epam.reportportal.base.infrastructure.persistence.dao.tms.TmsAttachmentRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.tms.TmsSyncJobRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.tms.TmsTestCaseRepository;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.Integration;
import com.epam.reportportal.base.infrastructure.persistence.entity.project.Project;
import com.epam.reportportal.base.infrastructure.persistence.entity.tms.TmsSyncJob;
import com.epam.reportportal.base.infrastructure.persistence.entity.tms.TmsTestCase;
import com.epam.reportportal.base.infrastructure.persistence.entity.tms.TmsTestCaseVersion;
import com.epam.reportportal.base.infrastructure.persistence.entity.tms.sync.SyncCounters;
import com.epam.reportportal.base.infrastructure.persistence.entity.tms.sync.SyncErrorLog;
import com.epam.reportportal.base.model.activity.TestCaseActivityResource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class TmsTestCaseSyncServiceImplTest {

  @Mock
  private TmsTestCaseRepository tmsTestCaseRepository;

  @Mock
  private TmsTestCaseVersionService tmsTestCaseVersionService;

  @Mock
  private TmsTestCaseAttributeService tmsTestCaseAttributeService;

  @Mock
  private TmsAttachmentDataStoreService tmsAttachmentDataStoreService;

  @Mock
  private TmsAttachmentRepository tmsAttachmentRepository;

  @Mock
  private TmsSyncJobRepository tmsSyncJobRepository;

  @Mock
  private TmsTestCaseMapper tmsTestCaseMapper;

  @Mock
  private TmsAttachmentMapper tmsAttachmentMapper;

  @Mock
  private TmsManualScenarioMapper tmsManualScenarioMapper;

  @Mock
  private TmsTestCaseActivityResourceMapper tmsTestCaseActivityResourceMapper;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private PlatformTransactionManager transactionManager;

  @Mock
  private TmsSyncConnector<Integration> connector;

  private TmsTestCaseSyncServiceImpl service;

  private final Long jobId = 1L;
  private final Long projectId = 10L;
  private final Long orgId = 100L;
  private Integration integration;
  private TmsSyncJob syncJob;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

    service = new TmsTestCaseSyncServiceImpl(
        tmsTestCaseRepository,
        tmsTestCaseVersionService,
        tmsTestCaseAttributeService,
        tmsAttachmentDataStoreService,
        tmsAttachmentRepository,
        tmsSyncJobRepository,
        tmsTestCaseMapper,
        tmsAttachmentMapper,
        tmsManualScenarioMapper,
        tmsTestCaseActivityResourceMapper,
        eventPublisher,
        transactionManager
    );

    var project = new Project();
    project.setId(projectId);
    project.setOrganizationId(orgId);

    integration = new Integration();
    integration.setId(5L);
    integration.setProject(project);

    syncJob = new TmsSyncJob();
    syncJob.setId(jobId);
    syncJob.setCounters(new SyncCounters(0, 0, 0));
    syncJob.setErrorLog(new SyncErrorLog(new ArrayList<>()));
  }

  @Test
  void processTestCaseBatch_WhenNewTestCase_ShouldPublishTestCaseCreatedEvent() {
    var remoteTc = RemoteTestCase.builder()
        .id("REMOTE-1")
        .name("Remote TC 1")
        .updatedAt(Instant.now())
        .build();
    var batch = List.of(remoteTc);

    var tmsTestCase = new TmsTestCase();
    tmsTestCase.setId(20L);
    tmsTestCase.setExternalId("REMOTE-1");
    tmsTestCase.setName("Remote TC 1");

    var defaultVersion = new TmsTestCaseVersion();
    defaultVersion.setId(200L);

    var activityResource = TestCaseActivityResource.builder()
        .id(20L)
        .name("Remote TC 1")
        .projectId(projectId)
        .build();

    var createdEvent = new TestCaseCreatedEvent(activityResource, null, null, orgId);

    when(tmsTestCaseRepository.findByProjectIdAndExternalIdIn(projectId, List.of("REMOTE-1")))
        .thenReturn(Collections.emptyList());
    when(tmsTestCaseMapper.convertFromRemote(remoteTc, null, projectId, null))
        .thenReturn(tmsTestCase);
    when(tmsTestCaseRepository.saveAll(anyList())).thenReturn(List.of(tmsTestCase));
    when(tmsManualScenarioMapper.convertFromRemote(eq(remoteTc), anyList()))
        .thenReturn((TmsTextManualScenarioRQ) mock(TmsManualScenarioRQ.class));
    when(tmsTestCaseVersionService.createDefaultTestCaseVersion(eq(projectId), eq(tmsTestCase), any()))
        .thenReturn(defaultVersion);
    when(tmsTestCaseActivityResourceMapper.buildActivityResource(tmsTestCase, defaultVersion))
        .thenReturn(activityResource);
    when(tmsTestCaseActivityResourceMapper.buildTestCaseCreatedEvent(orgId, null, null, activityResource))
        .thenReturn(createdEvent);
    when(tmsSyncJobRepository.findById(jobId)).thenReturn(Optional.of(syncJob));
    when(tmsSyncJobRepository.save(any(TmsSyncJob.class))).thenReturn(syncJob);

    service.processTestCaseBatch(jobId, projectId, connector, integration, batch, null);

    verify(eventPublisher, times(1)).publishEvent(createdEvent);
  }

  @Test
  void processTestCaseBatch_WhenExistingTestCaseUpdated_ShouldPublishFieldChangedEvent() {
    var now = Instant.now();
    var remoteTc = RemoteTestCase.builder()
        .id("REMOTE-1")
        .name("Updated TC 1")
        .updatedAt(now.plusSeconds(60))
        .build();
    var batch = List.of(remoteTc);

    var existingTestCase = new TmsTestCase();
    existingTestCase.setId(20L);
    existingTestCase.setExternalId("REMOTE-1");
    existingTestCase.setName("Old TC 1");
    existingTestCase.setSourceUpdatedAt(now);

    var beforeVersion = new TmsTestCaseVersion();
    beforeVersion.setId(199L);
    var updatedVersion = new TmsTestCaseVersion();
    updatedVersion.setId(200L);

    var beforeResource = TestCaseActivityResource.builder()
        .id(20L)
        .name("Old TC 1")
        .projectId(projectId)
        .build();
    var afterResource = TestCaseActivityResource.builder()
        .id(20L)
        .name("Updated TC 1")
        .projectId(projectId)
        .build();

    var fieldChangedEvent = new TestCaseFieldChangedEvent();

    when(tmsTestCaseRepository.findByProjectIdAndExternalIdIn(projectId, List.of("REMOTE-1")))
        .thenReturn(List.of(existingTestCase));
    when(tmsTestCaseVersionService.getDefaultVersions(List.of(20L)))
        .thenReturn(Map.of(20L, beforeVersion));
    when(tmsTestCaseActivityResourceMapper.buildActivityResource(existingTestCase, beforeVersion))
        .thenReturn(beforeResource);
    when(tmsTestCaseMapper.convertFromRemote(remoteTc, existingTestCase, projectId, null))
        .thenReturn(existingTestCase);
    when(tmsTestCaseRepository.saveAll(anyList())).thenReturn(List.of(existingTestCase));
    when(tmsManualScenarioMapper.convertFromRemote(eq(remoteTc), anyList()))
        .thenReturn(mock(TmsTextManualScenarioRQ.class));
    when(tmsTestCaseVersionService.updateDefaultTestCaseVersion(eq(projectId), eq(existingTestCase), any()))
        .thenReturn(updatedVersion);
    when(tmsTestCaseActivityResourceMapper.buildActivityResource(existingTestCase, updatedVersion))
        .thenReturn(afterResource);
    when(tmsTestCaseActivityResourceMapper.buildTestCaseFieldChangedEvents(orgId, null, null, beforeResource, afterResource))
        .thenReturn(List.of(fieldChangedEvent));
    when(tmsSyncJobRepository.findById(jobId)).thenReturn(Optional.of(syncJob));
    when(tmsSyncJobRepository.save(any(TmsSyncJob.class))).thenReturn(syncJob);

    service.processTestCaseBatch(jobId, projectId, connector, integration, batch, null);

    verify(eventPublisher, times(1)).publishEvent(fieldChangedEvent);
  }

  @Test
  void processTestCaseBatch_WhenNoUpdateNeeded_ShouldNotPublishEvents() {
    var now = Instant.now();
    var remoteTc = RemoteTestCase.builder()
        .id("REMOTE-1")
        .name("Same TC 1")
        .updatedAt(now)
        .build();
    var batch = List.of(remoteTc);

    var existingTestCase = new TmsTestCase();
    existingTestCase.setId(20L);
    existingTestCase.setExternalId("REMOTE-1");
    existingTestCase.setName("Same TC 1");
    existingTestCase.setSourceUpdatedAt(now);

    when(tmsTestCaseRepository.findByProjectIdAndExternalIdIn(projectId, List.of("REMOTE-1")))
        .thenReturn(List.of(existingTestCase));
    when(tmsTestCaseVersionService.getDefaultVersions(List.of(20L)))
        .thenReturn(Map.of());
    when(tmsSyncJobRepository.findById(jobId)).thenReturn(Optional.of(syncJob));
    when(tmsSyncJobRepository.save(any(TmsSyncJob.class))).thenReturn(syncJob);

    service.processTestCaseBatch(jobId, projectId, connector, integration, batch, null);

    verify(eventPublisher, never()).publishEvent(any());
  }
}

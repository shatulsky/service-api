/*
 * Copyright 2025 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.base.core.events.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.epam.reportportal.base.core.events.domain.tms.TestCaseCreatedEvent;
import com.epam.reportportal.base.core.events.domain.tms.TestCaseDeletedEvent;
import com.epam.reportportal.base.core.events.domain.tms.TestCaseFieldChangedEvent;
import com.epam.reportportal.base.infrastructure.persistence.entity.activity.Activity;
import com.epam.reportportal.base.infrastructure.persistence.entity.activity.ActivityAction;
import com.epam.reportportal.base.infrastructure.persistence.entity.activity.EventAction;
import com.epam.reportportal.base.infrastructure.persistence.entity.activity.EventObject;
import com.epam.reportportal.base.infrastructure.persistence.entity.activity.EventPriority;
import com.epam.reportportal.base.infrastructure.persistence.entity.activity.EventSubject;
import com.epam.reportportal.base.model.activity.TestCaseActivityResource;
import com.epam.reportportal.base.ws.rabbit.activity.converter.TestCaseCreatedEventConverter;
import com.epam.reportportal.base.ws.rabbit.activity.converter.TestCaseDeletedEventConverter;
import com.epam.reportportal.base.ws.rabbit.activity.converter.TestCaseFieldChangedEventConverter;
import org.junit.jupiter.api.Test;

class TestCaseEventsTest {

  private TestCaseActivityResource getResource() {
    return TestCaseActivityResource.builder()
        .id(10L)
        .name("Test Case 1")
        .description("Description")
        .priority("HIGH")
        .projectId(1L)
        .testFolderId(2L)
        .build();
  }

  @Test
  void testCaseCreatedEvent_WithUser_ConvertsSuccessfully() {
    var resource = getResource();
    var event = new TestCaseCreatedEvent(resource, 5L, "test_user", 100L);

    var converter = new TestCaseCreatedEventConverter();
    Activity activity = converter.convert(event);

    assertNotNull(activity);
    assertEquals(EventAction.CREATE, activity.getAction());
    assertEquals(ActivityAction.CREATE_TEST_CASE.getValue(), activity.getEventName());
    assertEquals(EventPriority.LOW, activity.getPriority());
    assertEquals(10L, activity.getObjectId());
    assertEquals("Test Case 1", activity.getObjectName());
    assertEquals(EventObject.TMS_TEST_CASE, activity.getObjectType());
    assertEquals(1L, activity.getProjectId());
    assertEquals(5L, activity.getSubjectId());
    assertEquals("test_user", activity.getSubjectName());
    assertEquals(EventSubject.USER, activity.getSubjectType());
    assertEquals(100L, activity.getOrganizationId());
  }

  @Test
  void testCaseCreatedEvent_SystemEvent_ConvertsSuccessfully() {
    var resource = getResource();
    var event = new TestCaseCreatedEvent(resource, null, null, 100L);

    var converter = new TestCaseCreatedEventConverter();
    Activity activity = converter.convert(event);

    assertNotNull(activity);
    assertEquals(EventAction.CREATE, activity.getAction());
    assertEquals(EventObject.TMS_TEST_CASE, activity.getObjectType());
    assertNull(activity.getSubjectId());
    assertEquals("ReportPortal", activity.getSubjectName());
    assertEquals(EventSubject.APPLICATION, activity.getSubjectType());
    assertEquals(100L, activity.getOrganizationId());
  }

  @Test
  void testCaseDeletedEvent_WithUser_ConvertsSuccessfully() {
    var resource = getResource();
    var event = new TestCaseDeletedEvent(resource, 5L, "test_user", 100L);

    var converter = new TestCaseDeletedEventConverter();
    Activity activity = converter.convert(event);

    assertNotNull(activity);
    assertEquals(EventAction.DELETE, activity.getAction());
    assertEquals(ActivityAction.DELETE_TEST_CASE.getValue(), activity.getEventName());
    assertEquals(EventPriority.MEDIUM, activity.getPriority());
    assertEquals(10L, activity.getObjectId());
    assertEquals(EventObject.TMS_TEST_CASE, activity.getObjectType());
    assertEquals(1L, activity.getProjectId());
    assertEquals(5L, activity.getSubjectId());
    assertEquals("test_user", activity.getSubjectName());
    assertEquals(EventSubject.USER, activity.getSubjectType());
    assertEquals(100L, activity.getOrganizationId());
  }

  @Test
  void testCaseDeletedEvent_SystemEvent_ConvertsSuccessfully() {
    var resource = getResource();
    var event = new TestCaseDeletedEvent(resource, null, null, 100L);

    var converter = new TestCaseDeletedEventConverter();
    Activity activity = converter.convert(event);

    assertNotNull(activity);
    assertEquals(EventAction.DELETE, activity.getAction());
    assertNull(activity.getSubjectId());
    assertEquals("ReportPortal", activity.getSubjectName());
    assertEquals(EventSubject.APPLICATION, activity.getSubjectType());
    assertEquals(100L, activity.getOrganizationId());
  }

  @Test
  void testCaseFieldChangedEvent_WithUser_ConvertsSuccessfully() {
    var resource = getResource();
    var event = new TestCaseFieldChangedEvent(
        resource, "name", EventAction.UPDATE, ActivityAction.UPDATE_TEST_CASE,
        "Old Name", "New Name", 5L, "test_user", 100L
    );

    var converter = new TestCaseFieldChangedEventConverter();
    Activity activity = converter.convert(event);

    assertNotNull(activity);
    assertEquals(EventAction.UPDATE, activity.getAction());
    assertEquals(ActivityAction.UPDATE_TEST_CASE.getValue(), activity.getEventName());
    assertEquals(EventObject.TMS_TEST_CASE, activity.getObjectType());
    assertEquals(5L, activity.getSubjectId());
    assertEquals("test_user", activity.getSubjectName());
    assertEquals(EventSubject.USER, activity.getSubjectType());
    assertEquals(100L, activity.getOrganizationId());
    assertNotNull(activity.getDetails());
    assertEquals(1, activity.getDetails().getHistory().size());
    assertEquals("name", activity.getDetails().getHistory().get(0).getField());
    assertEquals("Old Name", activity.getDetails().getHistory().get(0).getOldValue());
    assertEquals("New Name", activity.getDetails().getHistory().get(0).getNewValue());
  }

  @Test
  void testCaseFieldChangedEvent_SystemEvent_ConvertsSuccessfully() {
    var resource = getResource();
    var event = new TestCaseFieldChangedEvent(
        resource, "description", EventAction.UPDATE, ActivityAction.UPDATE_TEST_CASE,
        "Old Desc", "New Desc", null, null, 100L
    );

    var converter = new TestCaseFieldChangedEventConverter();
    Activity activity = converter.convert(event);

    assertNotNull(activity);
    assertEquals(EventAction.UPDATE, activity.getAction());
    assertNull(activity.getSubjectId());
    assertEquals("ReportPortal", activity.getSubjectName());
    assertEquals(EventSubject.APPLICATION, activity.getSubjectType());
    assertEquals(100L, activity.getOrganizationId());
    assertNotNull(activity.getDetails());
    assertEquals(1, activity.getDetails().getHistory().size());
  }
}

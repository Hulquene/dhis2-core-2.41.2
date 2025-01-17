/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.tracker.imports.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.util.List;
import org.hisp.dhis.DhisConvenienceTest;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.Enrollment;
import org.hisp.dhis.program.Event;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.relationship.RelationshipType;
import org.hisp.dhis.trackedentity.TrackedEntity;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.tracker.imports.domain.MetadataIdentifier;
import org.hisp.dhis.tracker.imports.domain.Relationship;
import org.hisp.dhis.tracker.imports.domain.RelationshipItem;
import org.hisp.dhis.tracker.imports.preheat.TrackerPreheat;
import org.hisp.dhis.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Enrico Colasante
 */
@ExtendWith(MockitoExtension.class)
class RelationshipTrackerConverterServiceTest extends DhisConvenienceTest {

  private static final String TE_TO_ENROLLMENT_RELATIONSHIP_TYPE = "xLmPUYJX8Ks";

  private static final String TE_TO_EVENT_RELATIONSHIP_TYPE = "TV9oB9LT3sh";

  private static final String TE = CodeGenerator.generateUid();

  private static final String ENROLLMENT = "ENROLLMENT_UID";

  private static final String EVENT = "EVENT_UID";

  private static final String RELATIONSHIP_A = "RELATIONSHIP_A_UID";

  private static final String RELATIONSHIP_B = "RELATIONSHIP_B_UID";

  private RelationshipType teToEnrollment;

  private RelationshipType teToEvent;

  private TrackedEntity trackedEntity;

  private Enrollment enrollment;

  private Event event;

  private TrackerConverterService<Relationship, org.hisp.dhis.relationship.Relationship>
      relationshipConverterService;

  private User user;

  @Mock public TrackerPreheat preheat;

  @BeforeEach
  protected void setupTest() {
    user = makeUser("A");
    OrganisationUnit organisationUnit = createOrganisationUnit('A');
    Program program = createProgram('A');
    TrackedEntityType teType = createTrackedEntityType('A');

    teToEnrollment = createTeToEnrollmentRelationshipType('A', program, teType, false);
    teToEnrollment.setUid(TE_TO_ENROLLMENT_RELATIONSHIP_TYPE);

    teToEvent = createTeToEventRelationshipType('B', program, teType, false);
    teToEvent.setUid(TE_TO_EVENT_RELATIONSHIP_TYPE);

    trackedEntity = createTrackedEntity(organisationUnit);
    trackedEntity.setTrackedEntityType(teType);
    trackedEntity.setUid(TE);
    enrollment = createEnrollment(program, trackedEntity, organisationUnit);
    enrollment.setUid(ENROLLMENT);
    event = createEvent(createProgramStage('A', program), enrollment, organisationUnit);
    event.setUid(EVENT);

    relationshipConverterService = new RelationshipTrackerConverterService();
  }

  @Test
  void testConverterFromRelationships() {
    when(preheat.getRelationship(RELATIONSHIP_A)).thenReturn(relationshipAFromDB());
    when(preheat.getRelationship(RELATIONSHIP_B)).thenReturn(relationshipBFromDB());
    when(preheat.getRelationshipType(MetadataIdentifier.ofUid(TE_TO_ENROLLMENT_RELATIONSHIP_TYPE)))
        .thenReturn(teToEnrollment);
    when(preheat.getRelationshipType(MetadataIdentifier.ofUid(TE_TO_EVENT_RELATIONSHIP_TYPE)))
        .thenReturn(teToEvent);
    when(preheat.getTrackedEntity(TE)).thenReturn(trackedEntity);
    when(preheat.getEnrollment(ENROLLMENT)).thenReturn(enrollment);
    when(preheat.getEvent(EVENT)).thenReturn(event);
    when(preheat.getUser()).thenReturn(user);

    List<org.hisp.dhis.relationship.Relationship> from =
        relationshipConverterService.from(preheat, List.of(relationshipA(), relationshipB()));
    assertNotNull(from);
    assertEquals(2, from.size());
    from.forEach(
        relationship -> {
          if (TE_TO_ENROLLMENT_RELATIONSHIP_TYPE.equals(
              relationship.getRelationshipType().getUid())) {
            assertEquals(TE, relationship.getFrom().getTrackedEntity().getUid());
            assertEquals(ENROLLMENT, relationship.getTo().getEnrollment().getUid());
          } else if (TE_TO_EVENT_RELATIONSHIP_TYPE.equals(
              relationship.getRelationshipType().getUid())) {
            assertEquals(TE, relationship.getFrom().getTrackedEntity().getUid());
            assertEquals(EVENT, relationship.getTo().getEvent().getUid());
          } else {
            fail("Unexpected relationshipType found.");
          }
          assertNotNull(relationship.getFrom());
          assertNotNull(relationship.getTo());
          assertEquals(user.getUid(), relationship.getLastUpdatedBy().getUid());
        });
  }

  @Test
  void testConverterToRelationships() {
    List<Relationship> to =
        relationshipConverterService.to(List.of(relationshipAFromDB(), relationshipBFromDB()));
    assertNotNull(to);
    assertEquals(2, to.size());
    to.forEach(
        relationship -> {
          if (TE_TO_ENROLLMENT_RELATIONSHIP_TYPE.equals(
              relationship.getRelationshipType().getIdentifier())) {
            assertEquals(TE, relationship.getFrom().getTrackedEntity());
            assertEquals(ENROLLMENT, relationship.getTo().getEnrollment());
          } else if (TE_TO_EVENT_RELATIONSHIP_TYPE.equals(
              relationship.getRelationshipType().getIdentifier())) {
            assertEquals(TE, relationship.getFrom().getTrackedEntity());
            assertEquals(EVENT, relationship.getTo().getEvent());
          } else {
            fail("Unexpected relationshipType found.");
          }
          assertNotNull(relationship.getFrom());
          assertNotNull(relationship.getTo());
        });
  }

  private Relationship relationshipA() {
    return Relationship.builder()
        .relationship(RELATIONSHIP_A)
        .relationshipType(MetadataIdentifier.ofUid(TE_TO_ENROLLMENT_RELATIONSHIP_TYPE))
        .from(RelationshipItem.builder().trackedEntity(TE).build())
        .to(RelationshipItem.builder().enrollment(ENROLLMENT).build())
        .build();
  }

  private Relationship relationshipB() {
    return Relationship.builder()
        .relationship(RELATIONSHIP_B)
        .relationshipType(MetadataIdentifier.ofUid(TE_TO_EVENT_RELATIONSHIP_TYPE))
        .from(RelationshipItem.builder().trackedEntity(TE).build())
        .to(RelationshipItem.builder().event(EVENT).build())
        .build();
  }

  private org.hisp.dhis.relationship.Relationship relationshipAFromDB() {
    return createTeToEnrollmentRelationship(trackedEntity, enrollment, teToEnrollment);
  }

  private org.hisp.dhis.relationship.Relationship relationshipBFromDB() {
    return createTeToEventRelationship(trackedEntity, event, teToEvent);
  }
}

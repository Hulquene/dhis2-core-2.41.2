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
package org.hisp.dhis.trackedentity;

import static org.hisp.dhis.common.OrganisationUnitSelectionMode.ACCESSIBLE;
import static org.hisp.dhis.tracker.TrackerTestUtils.uids;
import static org.hisp.dhis.utils.Assertions.assertContainsOnly;
import static org.hisp.dhis.utils.Assertions.assertIsEmpty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hisp.dhis.common.AccessLevel;
import org.hisp.dhis.feedback.BadRequestException;
import org.hisp.dhis.feedback.ForbiddenException;
import org.hisp.dhis.feedback.NotFoundException;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitService;
import org.hisp.dhis.program.EnrollmentService;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramService;
import org.hisp.dhis.security.Authorities;
import org.hisp.dhis.security.acl.AccessStringHelper;
import org.hisp.dhis.test.integration.IntegrationTestBase;
import org.hisp.dhis.tracker.export.trackedentity.TrackedEntityEnrollmentParams;
import org.hisp.dhis.tracker.export.trackedentity.TrackedEntityOperationParams;
import org.hisp.dhis.tracker.export.trackedentity.TrackedEntityParams;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserDetails;
import org.hisp.dhis.user.UserService;
import org.hisp.dhis.user.sharing.Sharing;
import org.hisp.dhis.user.sharing.UserAccess;
import org.hisp.dhis.utils.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Ameen Mohamed <ameen@dhis2.org>
 */
class TrackerOwnershipManagerTest extends IntegrationTestBase {

  @Autowired private TrackerOwnershipManager trackerOwnershipAccessManager;

  @Autowired private TrackerAccessManager trackerAccessManager;

  @Autowired private UserService _userService;

  @Autowired private TrackedEntityService entityInstanceService;

  @Autowired
  private org.hisp.dhis.tracker.export.trackedentity.TrackedEntityService trackedEntityService;

  @Autowired private OrganisationUnitService organisationUnitService;

  @Autowired private ProgramService programService;

  @Autowired private EnrollmentService enrollmentService;

  @Autowired private TrackedEntityTypeService trackedEntityTypeService;

  private TrackedEntity entityInstanceA1;

  private TrackedEntity entityInstanceB1;

  private OrganisationUnit organisationUnitA;

  private OrganisationUnit organisationUnitB;

  private Program programA;

  private Program programB;

  private User userA;

  private User userB;
  private User superUser;

  private UserDetails userDetailsA;

  private TrackedEntityParams defaultParams;

  @Override
  protected void setUpTest() throws Exception {
    //    userService = _userService;
    //    preCreateInjectAdminUser();

    organisationUnitA = createOrganisationUnit('A');
    organisationUnitService.addOrganisationUnit(organisationUnitA);
    organisationUnitB = createOrganisationUnit('B');
    organisationUnitService.addOrganisationUnit(organisationUnitB);

    TrackedEntityType trackedEntityType = createTrackedEntityType('A');
    trackedEntityTypeService.addTrackedEntityType(trackedEntityType);
    trackedEntityType.setSharing(Sharing.builder().publicAccess(AccessStringHelper.FULL).build());
    trackedEntityTypeService.updateTrackedEntityType(trackedEntityType);

    entityInstanceA1 = createTrackedEntity(organisationUnitA);
    entityInstanceA1.setTrackedEntityType(trackedEntityType);
    entityInstanceB1 = createTrackedEntity(organisationUnitB);
    entityInstanceB1.setTrackedEntityType(trackedEntityType);
    entityInstanceService.addTrackedEntity(entityInstanceA1);
    entityInstanceService.addTrackedEntity(entityInstanceB1);

    userA = createUserWithAuth("userA");
    userA.addOrganisationUnit(organisationUnitA);
    userService.updateUser(userA);
    userB = createUserWithAuth("userB");
    userB.addOrganisationUnit(organisationUnitB);
    userService.updateUser(userB);
    superUser = createAndAddAdminUser(Authorities.ALL.name());
    superUser.setOrganisationUnits(Set.of(organisationUnitA));
    userService.updateUser(superUser);

    programA = createProgram('A');
    programA.setAccessLevel(AccessLevel.PROTECTED);
    programA.setTrackedEntityType(trackedEntityType);
    programA.setOrganisationUnits(Set.of(organisationUnitA, organisationUnitB));
    programService.addProgram(programA);
    programA.setSharing(Sharing.builder().publicAccess(AccessStringHelper.FULL).build());
    programService.updateProgram(programA);
    programB = createProgram('B');
    programB.setAccessLevel(AccessLevel.CLOSED);
    programB.setTrackedEntityType(trackedEntityType);
    programService.addProgram(programB);
    programB.setSharing(
        Sharing.builder()
            .publicAccess(AccessStringHelper.DEFAULT)
            .users(Map.of(userB.getUid(), new UserAccess(userB, "r-r-----")))
            .build());
    programService.updateProgram(programB);

    userDetailsA = UserDetails.fromUser(userA);

    enrollmentService.addEnrollment(
        createEnrollment(programA, entityInstanceA1, organisationUnitA));

    defaultParams =
        new TrackedEntityParams(false, TrackedEntityEnrollmentParams.FALSE, false, false);
  }

  @Test
  void testAssignOwnership() {
    assertTrue(trackerOwnershipAccessManager.hasAccess(userA, entityInstanceA1, programA));
    assertFalse(trackerOwnershipAccessManager.hasAccess(userB, entityInstanceA1, programA));
    assertTrue(trackerOwnershipAccessManager.hasAccess(userB, entityInstanceB1, programA));
    trackerOwnershipAccessManager.assignOwnership(
        entityInstanceA1, programA, organisationUnitB, false, true);
    assertFalse(trackerOwnershipAccessManager.hasAccess(userA, entityInstanceA1, programA));
    assertTrue(trackerOwnershipAccessManager.hasAccess(userB, entityInstanceA1, programA));
  }

  @Test
  void testGrantTemporaryOwnershipWithAudit() {
    assertTrue(trackerOwnershipAccessManager.hasAccess(userA, entityInstanceA1, programA));
    assertFalse(trackerOwnershipAccessManager.hasAccess(userB, entityInstanceA1, programA));
    trackerOwnershipAccessManager.grantTemporaryOwnership(
        entityInstanceA1, programA, userB, "testing reason");
    assertTrue(trackerOwnershipAccessManager.hasAccess(userA, entityInstanceA1, programA));
    assertTrue(trackerOwnershipAccessManager.hasAccess(userA, entityInstanceA1, programA));
    assertTrue(trackerOwnershipAccessManager.hasAccess(userB, entityInstanceA1, programA));
  }

  @Test
  void shouldNotHaveAccessToEnrollmentWithUserAWhenTransferredToAnotherOrgUnit()
      throws ForbiddenException {
    userA.setTeiSearchOrganisationUnits(Set.of(organisationUnitB));
    userService.updateUser(userA);
    trackerOwnershipAccessManager.assignOwnership(
        entityInstanceA1, programA, organisationUnitA, false, true);
    trackerOwnershipAccessManager.transferOwnership(
        entityInstanceA1, programA, organisationUnitB, false, true);

    injectSecurityContextUser(userA);
    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class,
            () ->
                trackedEntityService.getTrackedEntity(
                    entityInstanceA1.getUid(), programA.getUid(), defaultParams, false));
    assertEquals("OWNERSHIP_ACCESS_DENIED", exception.getMessage());
  }

  @Test
  void shouldHaveAccessToEnrollmentWithUserBWhenTransferredToOwnOrgUnit()
      throws ForbiddenException, NotFoundException, BadRequestException {
    trackerOwnershipAccessManager.assignOwnership(
        entityInstanceA1, programA, organisationUnitA, false, true);
    trackerOwnershipAccessManager.transferOwnership(
        entityInstanceA1, programA, organisationUnitB, false, true);

    injectSecurityContextUser(userB);
    assertEquals(
        entityInstanceA1,
        trackedEntityService.getTrackedEntity(
            entityInstanceA1.getUid(), programA.getUid(), defaultParams, false));
  }

  @Test
  void shouldHaveAccessToEnrollmentWithSuperUserWhenTransferredToOwnOrgUnit()
      throws ForbiddenException, NotFoundException, BadRequestException {
    trackerOwnershipAccessManager.assignOwnership(
        entityInstanceA1, programA, organisationUnitA, false, true);
    trackerOwnershipAccessManager.transferOwnership(
        entityInstanceA1, programA, organisationUnitB, false, true);
    superUser.setOrganisationUnits(Set.of(organisationUnitB));
    userService.updateUser(superUser);

    injectSecurityContextUser(superUser);
    assertEquals(
        entityInstanceA1,
        trackedEntityService.getTrackedEntity(
            entityInstanceA1.getUid(), programA.getUid(), defaultParams, false));
  }

  @Test
  void shouldHaveAccessToTEWhenProgramNotProvidedButUserHasAccessToAtLeastOneProgram()
      throws ForbiddenException, NotFoundException, BadRequestException {
    injectSecurityContextUser(userA);

    assertEquals(
        entityInstanceA1,
        trackedEntityService.getTrackedEntity(
            entityInstanceA1.getUid(), null, defaultParams, false));
  }

  @Test
  void shouldNotHaveAccessToTEWhenProgramNotProvidedAndUserHasNoAccessToAnyProgram() {
    injectSecurityContextUser(userA);
    trackerOwnershipAccessManager.assignOwnership(
        entityInstanceA1, programA, organisationUnitB, false, true);

    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class,
            () ->
                trackedEntityService.getTrackedEntity(
                    entityInstanceA1.getUid(), null, defaultParams, false));

    assertEquals(
        String.format("User has no access to TrackedEntity:%s", entityInstanceA1.getUid()),
        exception.getMessage());
  }

  @Test
  void shouldHaveAccessWhenProgramProtectedAndUserInCaptureScope() {
    assertTrue(trackerOwnershipAccessManager.hasAccess(userA, entityInstanceA1, programA));
    assertTrue(
        trackerOwnershipAccessManager.hasAccess(
            userA, entityInstanceA1.getUid(), entityInstanceA1.getOrganisationUnit(), programA));
  }

  @Test
  void shouldHaveAccessWhenProgramProtectedAndHasTemporaryAccess() {
    trackerOwnershipAccessManager.grantTemporaryOwnership(
        entityInstanceA1, programA, userB, "test protected program");
    assertTrue(trackerOwnershipAccessManager.hasAccess(userB, entityInstanceA1, programA));
    assertTrue(
        trackerOwnershipAccessManager.hasAccess(
            userB, entityInstanceA1.getUid(), entityInstanceA1.getOrganisationUnit(), programA));
  }

  @Test
  void shouldNotHaveAccessWhenProgramProtectedAndUserNotInSearchScopeNorHasTemporaryAccess() {
    assertFalse(trackerOwnershipAccessManager.hasAccess(userB, entityInstanceA1, programA));
    assertFalse(
        trackerOwnershipAccessManager.hasAccess(
            userB, entityInstanceA1.getUid(), entityInstanceA1.getOrganisationUnit(), programA));

    injectSecurityContextUser(userB);
    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class,
            () ->
                trackedEntityService.getTrackedEntity(
                    entityInstanceA1.getUid(), programA.getUid(), defaultParams, false));
    assertEquals(TrackerOwnershipManager.NO_READ_ACCESS_TO_ORG_UNIT, exception.getMessage());
  }

  @Test
  void shouldNotHaveAccessWhenProgramProtectedAndUserNotInCaptureScopeNorHasTemporaryAccess() {
    userB.setTeiSearchOrganisationUnits(Set.of(organisationUnitA));
    userService.updateUser(userB);

    assertFalse(trackerOwnershipAccessManager.hasAccess(userB, entityInstanceA1, programA));
    assertFalse(
        trackerOwnershipAccessManager.hasAccess(
            userB, entityInstanceA1.getUid(), entityInstanceA1.getOrganisationUnit(), programA));

    injectSecurityContextUser(userB);
    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class,
            () ->
                trackedEntityService.getTrackedEntity(
                    entityInstanceA1.getUid(), programA.getUid(), defaultParams, false));
    assertEquals(TrackerOwnershipManager.OWNERSHIP_ACCESS_DENIED, exception.getMessage());
  }

  @Test
  void shouldHaveAccessWhenProgramClosedAndUserInCaptureScope() {
    assertTrue(trackerOwnershipAccessManager.hasAccess(userB, entityInstanceB1, programB));
    assertTrue(
        trackerOwnershipAccessManager.hasAccess(
            userB, entityInstanceB1.getUid(), entityInstanceB1.getOrganisationUnit(), programB));
  }

  @Test
  void shouldNotHaveAccessWhenProgramClosedAndUserHasTemporaryAccess() {
    trackerOwnershipAccessManager.grantTemporaryOwnership(
        entityInstanceA1, programB, userB, "test closed program");
    assertFalse(trackerOwnershipAccessManager.hasAccess(userB, entityInstanceA1, programB));
    assertFalse(
        trackerOwnershipAccessManager.hasAccess(
            userB, entityInstanceA1.getUid(), entityInstanceA1.getOrganisationUnit(), programB));

    injectSecurityContextUser(userB);
    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class,
            () ->
                trackedEntityService.getTrackedEntity(
                    entityInstanceA1.getUid(), programB.getUid(), defaultParams, false));
    assertEquals(TrackerOwnershipManager.PROGRAM_ACCESS_CLOSED, exception.getMessage());
  }

  @Test
  void shouldHaveAccessWhenProgramOpenAndUserInScope()
      throws ForbiddenException, NotFoundException, BadRequestException {
    programA.setAccessLevel(AccessLevel.OPEN);
    programService.updateProgram(programA);

    assertEquals(
        entityInstanceA1,
        trackedEntityService.getTrackedEntity(
            entityInstanceA1.getUid(), programA.getUid(), defaultParams, false));
  }

  @Test
  void shouldNotHaveAccessWhenProgramOpenAndUserNotInSearchScope() throws ForbiddenException {
    programA.setAccessLevel(AccessLevel.OPEN);
    programService.updateProgram(programA);
    trackerOwnershipAccessManager.transferOwnership(
        entityInstanceA1, programA, organisationUnitB, true, true);

    injectSecurityContextUser(userA);
    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class,
            () ->
                trackedEntityService.getTrackedEntity(
                    entityInstanceA1.getUid(), programA.getUid(), defaultParams, false));
    assertEquals(TrackerOwnershipManager.NO_READ_ACCESS_TO_ORG_UNIT, exception.getMessage());
  }

  @Test
  void shouldHaveAccessWhenProgramNotProvidedAndTEEnrolledButHaveAccessToTEOwner()
      throws ForbiddenException, NotFoundException, BadRequestException {
    trackerOwnershipAccessManager.transferOwnership(
        entityInstanceA1, programA, organisationUnitB, true, true);

    injectSecurityContextUser(userB);
    assertEquals(
        entityInstanceA1,
        trackedEntityService.getTrackedEntity(
            entityInstanceA1.getUid(), null, defaultParams, false));
  }

  @Test
  void shouldNotHaveAccessWhenProgramNotProvidedAndTEEnrolledAndNoAccessToTEOwner() {
    injectSecurityContextUser(userB);

    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class,
            () ->
                trackedEntityService.getTrackedEntity(
                    entityInstanceA1.getUid(), null, defaultParams, false));
    assertEquals(
        String.format("User has no access to TrackedEntity:%s", entityInstanceA1.getUid()),
        exception.getMessage());
  }

  @Test
  void shouldHaveAccessWhenProgramNotProvidedAndTENotEnrolledButHaveAccessToTeRegistrationUnit()
      throws ForbiddenException, NotFoundException, BadRequestException {
    injectSecurityContextUser(userB);

    assertEquals(
        entityInstanceB1,
        trackedEntityService.getTrackedEntity(
            entityInstanceB1.getUid(), null, defaultParams, false));
  }

  @Test
  void shouldNotHaveAccessWhenProgramNotProvidedAndTENotEnrolledAndNoAccessToTeRegistrationUnit() {
    injectSecurityContextUser(userA);

    ForbiddenException exception =
        assertThrows(
            ForbiddenException.class,
            () ->
                trackedEntityService.getTrackedEntity(
                    entityInstanceB1.getUid(), null, defaultParams, false));
    assertEquals(
        String.format("User has no access to TrackedEntity:%s", entityInstanceB1.getUid()),
        exception.getMessage());
  }

  @Test
  void shouldFindTrackedEntityWhenTransferredToAccessibleOrgUnit()
      throws ForbiddenException, BadRequestException, NotFoundException {
    transferOwnership(entityInstanceA1, programA, organisationUnitB);
    TrackedEntityOperationParams operationParams = createOperationParams(userB, null);

    List<String> trackedEntities = getTrackedEntities(operationParams);

    assertContainsOnly(
        List.of(entityInstanceA1.getUid(), entityInstanceB1.getUid()), trackedEntities);
  }

  @Test
  void shouldFindTrackedEntityWhenTransferredToAccessibleOrgUnitAndSuperUser()
      throws ForbiddenException, BadRequestException, NotFoundException {
    transferOwnership(entityInstanceA1, programA, organisationUnitB);
    superUser.setOrganisationUnits(Set.of(organisationUnitB));
    userService.updateUser(superUser);
    TrackedEntityOperationParams operationParams = createOperationParams(superUser, null);

    List<String> trackedEntities = getTrackedEntities(operationParams);

    assertContainsOnly(
        List.of(entityInstanceA1.getUid(), entityInstanceB1.getUid()), trackedEntities);
  }

  @Test
  void shouldNotFindTrackedEntityWhenTransferredToInaccessibleOrgUnit()
      throws ForbiddenException, BadRequestException, NotFoundException {
    transferOwnership(entityInstanceA1, programA, organisationUnitB);

    TrackedEntityOperationParams operationParams = createOperationParams(userA, null);
    Assertions.assertIsEmpty(getTrackedEntities(operationParams));
  }

  @Test
  void shouldFindTrackedEntityWhenTransferredToInaccessibleOrgUnitIfHasReadAccessToOtherProgram()
      throws ForbiddenException, BadRequestException, NotFoundException {
    transferOwnership(entityInstanceA1, programA, organisationUnitB);
    programB.setSharing(Sharing.builder().publicAccess(AccessStringHelper.DATA_READ).build());
    programService.updateProgram(programB);
    TrackedEntityOperationParams operationParams = createOperationParams(userA, null);

    trackerAccessManager.canRead(userA, entityInstanceA1);

    List<String> trackedEntities = getTrackedEntities(operationParams);

    assertContainsOnly(List.of(entityInstanceA1.getUid()), trackedEntities);
  }

  @Test
  void shouldFindTrackedEntityWhenTransferredToInaccessibleOrgUnitIfSuperUser()
      throws ForbiddenException, BadRequestException, NotFoundException {
    transferOwnership(entityInstanceA1, programA, organisationUnitB);
    TrackedEntityOperationParams operationParams = createOperationParams(superUser, null);

    List<String> trackedEntities = getTrackedEntities(operationParams);

    assertContainsOnly(List.of(entityInstanceA1.getUid()), trackedEntities);
  }

  @Test
  void shouldNotTransferOwnershipWhenOrgUnitNotAssociatedToProgram() {
    OrganisationUnit notAssociatedOrgUnit = createOrganisationUnit('C');
    organisationUnitService.addOrganisationUnit(notAssociatedOrgUnit);
    Exception exception =
        assertThrows(
            ForbiddenException.class,
            () -> transferOwnership(entityInstanceA1, programA, notAssociatedOrgUnit));
    assertEquals(
        String.format(
            "The program %s is not associated to the org unit %s",
            programA.getUid(), notAssociatedOrgUnit.getUid()),
        exception.getMessage());
  }

  @Test
  void shouldFindTrackedEntityWhenProgramSuppliedAndUserIsOwner()
      throws ForbiddenException, BadRequestException, NotFoundException {
    assignOwnership(entityInstanceA1, programA, organisationUnitA);
    TrackedEntityOperationParams operationParams = createOperationParams(userA, programA.getUid());
    injectSecurityContext(userDetailsA);

    List<String> trackedEntities = getTrackedEntities(operationParams);

    assertContainsOnly(List.of(entityInstanceA1.getUid()), trackedEntities);
  }

  @Test
  void shouldNotFindTrackedEntityWhenProgramSuppliedAndUserIsNotOwner()
      throws ForbiddenException, BadRequestException, NotFoundException {
    assignOwnership(entityInstanceA1, programA, organisationUnitA);
    TrackedEntityOperationParams operationParams = createOperationParams(userB, programA.getUid());
    injectSecurityContext(userDetailsA);

    assertIsEmpty(getTrackedEntities(operationParams));
  }

  private void transferOwnership(
      TrackedEntity trackedEntity, Program program, OrganisationUnit orgUnit)
      throws ForbiddenException {
    trackerOwnershipAccessManager.transferOwnership(trackedEntity, program, orgUnit, false, true);
  }

  private void assignOwnership(
      TrackedEntity trackedEntity, Program program, OrganisationUnit orgUnit) {
    trackerOwnershipAccessManager.assignOwnership(trackedEntity, program, orgUnit, false, true);
  }

  private TrackedEntityOperationParams createOperationParams(User user, String programUid) {
    return TrackedEntityOperationParams.builder()
        .trackedEntityTypeUid(entityInstanceA1.getTrackedEntityType().getUid())
        .orgUnitMode(ACCESSIBLE)
        .user(user)
        .programUid(programUid)
        .build();
  }

  private List<String> getTrackedEntities(TrackedEntityOperationParams params)
      throws ForbiddenException, BadRequestException, NotFoundException {
    return uids(trackedEntityService.getTrackedEntities(params));
  }
}

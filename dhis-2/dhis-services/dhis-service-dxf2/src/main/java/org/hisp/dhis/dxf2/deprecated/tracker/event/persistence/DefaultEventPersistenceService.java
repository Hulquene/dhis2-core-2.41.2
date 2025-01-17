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
package org.hisp.dhis.dxf2.deprecated.tracker.event.persistence;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hisp.dhis.dxf2.deprecated.tracker.event.EventCommentStore;
import org.hisp.dhis.dxf2.deprecated.tracker.event.EventStore;
import org.hisp.dhis.dxf2.deprecated.tracker.importer.context.WorkContext;
import org.hisp.dhis.dxf2.deprecated.tracker.importer.mapper.ProgramStageInstanceMapper;
import org.hisp.dhis.eventdatavalue.EventDataValue;
import org.hisp.dhis.program.Event;
import org.hisp.dhis.program.UserInfoSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Luciano Fiandesio
 */
@Service
@RequiredArgsConstructor
public class DefaultEventPersistenceService implements EventPersistenceService {
  @Nonnull private final EventStore jdbcEventStore;

  @Nonnull private final EventCommentStore jdbcEventCommentStore;

  @Nonnull private final EntityManager entityManager;

  @Nonnull private final ObjectMapper mapper;

  @Override
  @Transactional
  public void save(
      WorkContext context, List<org.hisp.dhis.dxf2.deprecated.tracker.event.Event> events) {
    if (isNotEmpty(events)) {
      ProgramStageInstanceMapper mapper = new ProgramStageInstanceMapper(context);

      List<Event> programStageInstances =
          jdbcEventStore.saveEvents(events.stream().map(mapper::map).collect(Collectors.toList()));

      jdbcEventCommentStore.saveAllComments(programStageInstances);

      if (!context.getImportOptions().isSkipLastUpdated()) {
        updateTeis(context, events);
      }
    }
  }

  /**
   * Updates the list of given events using a single transaction.
   *
   * @param context a {@see WorkContext}
   * @param events a List of {@see Event}
   */
  @Override
  @Transactional
  public void update(
      final WorkContext context,
      final List<org.hisp.dhis.dxf2.deprecated.tracker.event.Event> events) {
    if (isNotEmpty(events)) {
      ProgramStageInstanceMapper mapper = new ProgramStageInstanceMapper(context);

      List<Event> programStageInstances =
          jdbcEventStore.updateEvents(
              events.stream().map(mapper::map).collect(Collectors.toList()));

      jdbcEventCommentStore.saveAllComments(programStageInstances);

      if (!context.getImportOptions().isSkipLastUpdated()) {
        updateTeis(context, events);
      }
    }
  }

  /**
   * Deletes the list of events using a single transaction.
   *
   * @param context a {@see WorkContext}
   * @param events a List of {@see Event}
   */
  @Override
  @Transactional
  public void delete(
      final WorkContext context,
      final List<org.hisp.dhis.dxf2.deprecated.tracker.event.Event> events) {
    if (isNotEmpty(events)) {
      jdbcEventStore.delete(events);
    }
  }

  @Override
  @Transactional
  public void updateEventDataValues(
      EventDataValue de,
      org.hisp.dhis.dxf2.deprecated.tracker.event.Event event,
      WorkContext workContext)
      throws JsonProcessingException {
    String query;
    String deUid = de.getDataElement();

    if (de.getValue() == null) {
      // delete
      query =
          String.format(
              " UPDATE event SET eventdatavalues = eventdatavalues\\:\\:jsonb - '%1$s'"
                  + ", lastupdated = current_timestamp"
                  + ", lastupdatedbyuserinfo = CAST(:lastupdatedbyuserinfo as jsonb) "
                  + " WHERE  uid = :event",
              deUid);

      entityManager
          .createNativeQuery(query)
          .setParameter("event", event.getEvent())
          .setParameter(
              "lastupdatedbyuserinfo",
              mapper.writeValueAsString(
                  UserInfoSnapshot.from(workContext.getImportOptions().getUser())))
          .executeUpdate();
    } else {
      // merge
      de.setDataElement(null); // de uid is used as a key in the json, so we don't need it here
      de.setValue(
          de.getValue()
              .replace(
                  "'", "''")); // escape single quote used also as a json delimiter in the query

      query =
          String.format(
              " UPDATE event SET"
                  + " eventdatavalues= (CASE"
                  + " WHEN eventdatavalues->:de IS NOT NULL"
                  + " THEN jsonb_set(eventdatavalues, '{%1$s}', (eventdatavalues->:de) || '%2$s')"
                  + " WHEN eventdatavalues->:de IS NULL"
                  + " THEN jsonb_insert(eventdatavalues, '{%1$s}', '%2$s')"
                  + " END) ,"
                  + " lastupdated = current_timestamp"
                  + " ,lastupdatedbyuserinfo = CAST(:lastupdatedbyuserinfo as jsonb)"
                  + " WHERE  uid = :event",
              deUid, mapper.writeValueAsString(de));

      entityManager
          .createNativeQuery(query)
          .setParameter("event", event.getEvent())
          .setParameter("de", deUid)
          .setParameter(
              "lastupdatedbyuserinfo",
              mapper.writeValueAsString(
                  UserInfoSnapshot.from(workContext.getImportOptions().getUser())))
          .executeUpdate();
    }
  }

  @Override
  public void updateTrackedEntityInstances(
      WorkContext context, List<org.hisp.dhis.dxf2.deprecated.tracker.event.Event> events) {
    updateTeis(context, events);
  }

  /**
   * Updates the "lastupdated" and "lastupdatedBy" of the Tracked Entity Instances linked to the
   * provided list of Events.
   *
   * @param context a {@see WorkContext}
   * @param events a List of {@see Event}
   */
  private void updateTeis(
      final WorkContext context,
      final List<org.hisp.dhis.dxf2.deprecated.tracker.event.Event> events) {
    // Make sure that the TEI uids are not duplicated
    final List<String> distinctTeiList =
        events.stream()
            .map(e -> context.getTrackedEntityInstance(e.getUid()))
            .filter(Optional::isPresent)
            .map(o -> o.get().getUid())
            .distinct()
            .collect(Collectors.toList());

    jdbcEventStore.updateTrackedEntityInstances(
        distinctTeiList, context.getImportOptions().getUser());
  }
}

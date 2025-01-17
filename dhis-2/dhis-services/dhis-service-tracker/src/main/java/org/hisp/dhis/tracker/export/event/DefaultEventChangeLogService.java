/*
 * Copyright (c) 2004-2024, University of Oslo
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
package org.hisp.dhis.tracker.export.event;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hisp.dhis.common.UID;
import org.hisp.dhis.feedback.NotFoundException;
import org.hisp.dhis.program.Event;
import org.hisp.dhis.trackedentity.TrackerAccessManager;
import org.hisp.dhis.tracker.export.Page;
import org.hisp.dhis.tracker.export.PageParams;
import org.hisp.dhis.user.CurrentUserUtil;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("org.hisp.dhis.tracker.export.event.EventChangeLogService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DefaultEventChangeLogService implements EventChangeLogService {

  private final org.hisp.dhis.program.EventService eventService;

  private final JdbcEventChangeLogStore jdbcEventChangeLogStore;

  private final TrackerAccessManager trackerAccessManager;

  private final UserService userService;

  @Override
  public Page<EventChangeLog> getEventChangeLog(
      UID eventUid, EventChangeLogOperationParams operationParams, PageParams pageParams)
      throws NotFoundException {
    Event event = eventService.getEvent(eventUid.getValue());
    if (event == null) {
      throw new NotFoundException(Event.class, eventUid.getValue());
    }

    User currentUser = userService.getUserByUsername(CurrentUserUtil.getCurrentUsername());
    List<String> errors = trackerAccessManager.canRead(currentUser, event, false);
    if (!errors.isEmpty()) {
      throw new NotFoundException(Event.class, eventUid.getValue());
    }

    return jdbcEventChangeLogStore.getEventChangeLog(
        eventUid, operationParams.getOrder(), pageParams);
  }

  @Override
  public Set<String> getOrderableFields() {
    return jdbcEventChangeLogStore.getOrderableFields();
  }
}

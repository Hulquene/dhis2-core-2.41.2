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
package org.hisp.dhis.programrule;

import java.util.List;
import java.util.Set;
import org.hisp.dhis.common.IdentifiableObjectStore;
import org.hisp.dhis.program.Program;

/**
 * @author markusbekken
 */
public interface ProgramRuleStore extends IdentifiableObjectStore<ProgramRule> {
  /**
   * Get programRule by program
   *
   * @param program {@link Program}
   * @return ProgramRuleVariable list
   */
  List<ProgramRule> get(Program program);

  /**
   * Returns a {@link ProgramRule}.
   *
   * @param name the name of the ProgramRule to return.
   * @param program {@link Program}.
   * @return the ProgramRule with the given name
   */
  ProgramRule getByName(String name, Program program);

  /**
   * Get validation by {@link Program}
   *
   * @param program Program
   * @param key Search Program Rule by key
   * @return ProgramRule list
   */
  List<ProgramRule> get(Program program, String key);

  List<ProgramRule> getProgramRulesByActionTypes(
      Program program, Set<ProgramRuleActionType> actionTypes);

  List<ProgramRule> getProgramRulesByActionTypes(
      Program program, Set<ProgramRuleActionType> actionTypes, String programStageUid);

  List<String> getDataElementsPresentInProgramRules(Set<ProgramRuleActionType> actionTypes);

  List<String> getTrackedEntityAttributesPresentInProgramRules(
      Set<ProgramRuleActionType> actionTypes);

  List<ProgramRule> getProgramRulesByEvaluationTime(ProgramRuleActionEvaluationTime evaluationTime);

  List<ProgramRule> getProgramRulesByEvaluationEnvironment(
      ProgramRuleActionEvaluationEnvironment environment);

  List<ProgramRule> getProgramRulesWithNoCondition();

  List<ProgramRule> getProgramRulesWithNoPriority();

  List<ProgramRule> getProgramRulesWithNoAction();
}

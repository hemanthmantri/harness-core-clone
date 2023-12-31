/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.ticket;

import io.harness.ssca.beans.ticket.TicketRequestDto;
import io.harness.ssca.beans.ticket.TicketResponseDto;

import java.util.List;
import java.util.Map;

public interface TicketServiceRestClientService {
  TicketResponseDto createTicket(
      String authToken, String accountId, String orgId, String projectId, TicketRequestDto ticketRequestDto);

  List<TicketResponseDto> getTickets(String authToken, String module, Map<String, List<String>> identifiers,
      String accountId, String orgId, String projectId);
}

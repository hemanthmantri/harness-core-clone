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
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface TicketServiceRestClient {
  @POST("tickets")
  Call<TicketResponseDto> createTicket(@Query("accountId") String accountId, @Query("orgId") String orgId,
      @Query("projectId") String projectId, @Body TicketRequestDto ticketRequestDto);

  @GET("tickets")
  Call<List<TicketResponseDto>> getTickets(@Query("module") String module,
      @Query("identifiers") Map<String, List<String>> identifiers, @Query("accountId") String accountId,
      @Query("orgId") String orgId, @Query("projectId") String projectId);
}

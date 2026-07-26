package au.crewcomp.api

import au.crewcomp.notify.NotificationService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * ADM-8 — the notifications centre (§9, §10.2 `/notifications`).
 *
 * Every endpoint here answers **for the caller only**. There is deliberately no
 * `?userAccountId=` and no "notifications for person X": a notification list is a record of what
 * someone has been told, and one person reading another's is a privacy problem with no
 * corresponding use. The mobile app's list comes through the sync path (§10.3) for the same reason.
 *
 * Rooted at `/api/v1` rather than `/api/v1/notifications`, per the JAX-RS prefix trap documented on
 * [ComplianceResource].
 */
@Path("/api/v1")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Notifications", description = "The signed-in actor's notifications (§9, ADM-8)")
class NotificationResource(private val notifications: NotificationService) {

    @GET
    @Path("/notifications")
    @Operation(summary = "The caller's notifications, newest first")
    fun list(
        @QueryParam("state") @DefaultValue("all") state: String,
    ): List<AdminNotificationDto> {
        val all = notifications.forActor().map { it.toAdminDto() }
        return when (state) {
            "unread" -> all.filter { !it.read }
            "read" -> all.filter { it.read }
            "all" -> all
            else -> throw IllegalArgumentException("state is unread, read or all — not '$state'")
        }
    }

    /**
     * The unread count, for the shell's badge.
     *
     * Its own endpoint rather than a header on every response: the badge is polled, the list is not,
     * and a count query is a great deal cheaper than the rows.
     */
    @GET
    @Path("/notifications/summary")
    @Operation(summary = "Total and unread counts for the caller")
    fun summary(): NotificationSummaryDto = NotificationSummaryDto(
        total = notifications.forActor().size,
        unread = notifications.unreadCountForActor(),
    )

    /**
     * Marks one notification read.
     *
     * 404 when it is not the caller's, rather than 403 — absent and invisible must answer alike, or
     * the status code enumerates other people's notification ids.
     */
    @POST
    @Path("/notifications/{notificationId}/read")
    @Operation(summary = "Mark one of the caller's notifications read")
    fun markRead(@PathParam("notificationId") notificationId: Long): Response =
        if (notifications.markReadForActor(notificationId)) {
            Response.noContent().build()
        } else {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorDto("not_found", "No notification $notificationId for this user"))
                .build()
        }

    @POST
    @Path("/notifications/read-all")
    @Operation(summary = "Mark everything the caller can see as read")
    fun markAllRead(): NotificationSummaryDto {
        notifications.markAllReadForActor()
        return NotificationSummaryDto(
            total = notifications.forActor().size,
            unread = notifications.unreadCountForActor(),
        )
    }
}

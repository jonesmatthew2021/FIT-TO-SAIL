package au.crewcomp.api

import au.crewcomp.sync.SyncService
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType

/**
 * §10.3 mobile sync endpoints.
 *
 * Note what is **not** here: any way to say whose data to sync. The service answers for the
 * authenticated crew member, so there is no path parameter, query parameter or body field a
 * caller could put someone else's id in (AUTH-2).
 *
 * Rooted at `/api/v1/sync` as its own resource class. JAX-RS selects one root resource by path
 * prefix and then matches sub-paths only within it — putting these methods on the class rooted
 * at `/api/v1` would make them unreachable, silently, with no start-up warning. That trap has
 * already cost this codebase an afternoon; see backend/CLAUDE.md.
 */
@Path("/api/v1/sync")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
class SyncResource(private val sync: SyncService) {

    /**
     * The full scoped dataset and a cursor.
     *
     * Called on first sign-in, after a local-store wipe, and whenever a delta reports the
     * reference data has moved.
     */
    @GET
    @Path("/snapshot")
    fun snapshot(): SyncSnapshotDto = sync.snapshot()

    /**
     * Everything that changed after [cursor].
     *
     * `cursor=0` is legal and equivalent to a snapshot's worth of person-scoped rows without the
     * reference payload — which is what a client that lost its cursor but kept its rows wants.
     */
    @GET
    @Path("/delta")
    fun delta(@QueryParam("cursor") @DefaultValue("0") cursor: Long): SyncDeltaDto =
        sync.delta(cursor)

    /**
     * Applies the device's outbound queue.
     *
     * Always 200: the per-operation verdicts in the body are the result, because a batch in
     * which one entry is malformed and forty are fine is not a failed request. See
     * [SyncQueueResultDto].
     */
    @POST
    @Path("/queue")
    @Consumes(MediaType.APPLICATION_JSON)
    fun queue(request: SyncQueueRequest): SyncQueueResultDto = sync.enqueue(request)
}

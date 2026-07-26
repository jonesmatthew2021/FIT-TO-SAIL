package au.crewcomp.api

import au.crewcomp.people.UserAdminService
import au.crewcomp.platform.config.ConfigKey
import au.crewcomp.platform.config.ConfigService
import au.crewcomp.platform.jobs.JobHealth
import au.crewcomp.platform.security.Role
import io.quarkus.security.Authenticated
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * ADM-10 — administration: configuration, scheduled-job health, users and roles, and the SEC-1a
 * identity allow-list (§6, §10.2 `/users /config`).
 *
 * Rooted at `/api/v1/administration`. Nothing else is rooted beneath it, which is what makes a longer
 * prefix safe here — see the JAX-RS note on [ComplianceResource].
 */
@Path("/api/v1/administration")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Administration", description = "Configuration, job health, users and roles (ADM-10)")
class AdministrationResource(
    // Named apart from the methods below on purpose: `fun jobs()` beside a `jobs` property compiles
    // and reads as a coin toss, and this codebase has already paid once for a name that resolved to
    // the wrong thing.
    private val settings: ConfigService,
    private val jobHealth: JobHealth,
    private val userAdmin: UserAdminService,
) {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    @GET
    @Path("/config")
    @Operation(summary = "Every setting with its effective value, default, and whether it is overridden")
    fun config(): List<ConfigSettingDto> = settings.list().map { it.toDto() }

    @PUT
    @Path("/config/{key}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set one setting — Compliance Lead or System Administrator, audited")
    fun setConfig(@PathParam("key") key: String, request: SetConfigRequest): ConfigSettingDto =
        settings.set(configKey(key), request.value).toDto()

    /**
     * Clears an override, restoring the code default.
     *
     * A separate verb rather than `PUT` with a null value, because the two mean different things to
     * a reader and one of them is the safe way out of a bad setting: "put it back" should not require
     * knowing what the default was.
     */
    @DELETE
    @Path("/config/{key}")
    @Operation(summary = "Clear an override, restoring the built-in default")
    fun clearConfig(@PathParam("key") key: String): ConfigSettingDto =
        settings.set(configKey(key), null).toDto()

    private fun configKey(key: String): ConfigKey =
        ConfigKey.entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException(
                "'$key' is not a configurable setting; the settings are " +
                    ConfigKey.entries.joinToString { it.key },
            )

    // -----------------------------------------------------------------------
    // Scheduled jobs (§9, MCP-1)
    // -----------------------------------------------------------------------

    @GET
    @Path("/jobs")
    @Operation(summary = "The scheduled jobs, their cadence, and the outcome of their last run")
    fun jobs(): List<ScheduledJobDto> {
        val lastRuns = jobHealth.lastRuns()
        return jobHealth.scheduled().map { it.toDto(lastRuns[it.name]) }
    }

    @POST
    @Path("/jobs/{name}/run")
    @Operation(summary = "Run a job now — System Administrator only")
    fun runJob(@PathParam("name") name: String): JobRunDto = jobHealth.runNow(name).toDto()

    // -----------------------------------------------------------------------
    // Users and roles (AUTH-4)
    // -----------------------------------------------------------------------

    @GET
    @Path("/users")
    @Operation(summary = "Every account with its roles, scopes and identity linkage")
    fun users(): List<UserAccountDto> = userAdmin.listAccounts().map { it.toDto() }

    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Create a SEC-1b transitional account — refused in production, where accounts " +
            "are created at first corporate sign-in (ADR 0003)",
    )
    fun createUser(request: CreateTransitionalAccountRequest): UserAccountDto =
        userAdmin.createTransitionalAccount(
            displayName = request.displayName,
            email = request.email,
            roles = request.roles.map { Role.fromWire(it) }.toSet(),
            personId = request.personId,
        ).toDto()

    @POST
    @Path("/users/{userAccountId}/roles/{role}")
    @Operation(summary = "Grant a role — audited with the granting actor (AUTH-4)")
    fun grantRole(
        @PathParam("userAccountId") userAccountId: Long,
        @PathParam("role") role: String,
    ): UserAccountDto = userAdmin.grantRole(userAccountId, Role.fromWire(role)).toDto()

    @DELETE
    @Path("/users/{userAccountId}/roles/{role}")
    @Operation(summary = "Revoke a role — refuses to remove the last System Administrator")
    fun revokeRole(
        @PathParam("userAccountId") userAccountId: Long,
        @PathParam("role") role: String,
    ): UserAccountDto = userAdmin.revokeRole(userAccountId, Role.fromWire(role)).toDto()

    @PUT
    @Path("/users/{userAccountId}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Suspend or reactivate an account — accounts are never deleted")
    fun setStatus(
        @PathParam("userAccountId") userAccountId: Long,
        request: SetAccountStatusRequest,
    ): UserAccountDto = userAdmin.setStatus(userAccountId, request.status).toDto()

    @PUT
    @Path("/users/{userAccountId}/scopes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set the partnerships a Vessel Master is scoped to (§3)")
    fun setScopes(
        @PathParam("userAccountId") userAccountId: Long,
        request: SetScopesRequest,
    ): UserAccountDto = userAdmin.setPartnershipScopes(userAccountId, request.partnershipIds.toSet()).toDto()

    // -----------------------------------------------------------------------
    // SEC-1a — the identity allow-list
    // -----------------------------------------------------------------------

    @GET
    @Path("/identity-providers")
    @Operation(summary = "The corporate identity backends — this list is the SEC-1a allow-list")
    fun identityProviders(): List<IdentityProviderDto> =
        userAdmin.listIdentityProviders().map { it.toDto() }

    @POST
    @Path("/identity-providers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Onboard an identity backend, disabled — enabling it is a separate act")
    fun createIdentityProvider(request: CreateIdentityProviderRequest): IdentityProviderDto =
        userAdmin.createIdentityProvider(
            provider = request.provider,
            issuer = request.issuer,
            tenantOrDomain = request.tenantOrDomain,
            displayName = request.displayName,
        ).toDto()

    @PUT
    @Path("/identity-providers/{identityProviderId}/enabled")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Allow or stop an identity backend from authenticating anyone (SEC-1a)")
    fun setEnabled(
        @PathParam("identityProviderId") identityProviderId: Long,
        request: SetEnabledRequest,
    ): IdentityProviderDto =
        userAdmin.setIdentityProviderEnabled(identityProviderId, request.enabled).toDto()
}

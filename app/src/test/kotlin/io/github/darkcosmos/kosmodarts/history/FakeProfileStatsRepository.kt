package io.github.darkcosmos.kosmodarts.history

/** In-memory [ProfileStatsRepository]: serves the [profiles] by id; a profile that is not there is a failure. */
class FakeProfileStatsRepository(var profiles: Map<Long, ProfileStatsData> = emptyMap()) : ProfileStatsRepository {
    override fun load(profileId: Long): DbResult<ProfileStatsData> =
        profiles[profileId]?.let { DbResult.Ok(it) } ?: DbResult.Failed
}

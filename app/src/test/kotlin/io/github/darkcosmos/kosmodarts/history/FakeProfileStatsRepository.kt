package io.github.darkcosmos.kosmodarts.history

/** In-memory [ProfileStatsRepository]: serves the [profiles] by id; a profile that is not there is a failure. */
class FakeProfileStatsRepository(
    var profiles: Map<Long, ProfileStatsData> = emptyMap(),
    var pickers: Map<Long, HeadToHeadPicker> = emptyMap(),
    var pairs: Map<Pair<Long, Long>, HeadToHeadData> = emptyMap(),
) : ProfileStatsRepository {
    override fun load(profileId: Long): DbResult<ProfileStatsData> =
        profiles[profileId]?.let { DbResult.Ok(it) } ?: DbResult.Failed

    override fun loadPicker(profileId: Long): DbResult<HeadToHeadPicker> =
        pickers[profileId]?.let { DbResult.Ok(it) } ?: DbResult.Failed

    override fun loadPair(a: Long, b: Long): DbResult<HeadToHeadData> =
        pairs[a to b]?.let { DbResult.Ok(it) } ?: DbResult.Failed
}

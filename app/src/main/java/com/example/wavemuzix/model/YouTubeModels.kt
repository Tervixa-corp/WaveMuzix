package com.example.wavemuzix.model

import com.google.gson.annotations.SerializedName

// Common Components
data class YTThumbnail(
    val url: String,
    val width: Int,
    val height: Int
)

data class YTThumbnails(
    val thumbnails: List<YTThumbnail>?
) {
    fun getBestThumbnail(): String? = thumbnails?.lastOrNull()?.url
}

data class YTRun(
    val text: String
)

data class YTRuns(
    val runs: List<YTRun>? = null,
    val simpleText: String? = null
) {
    fun getText(): String = simpleText ?: runs?.joinToString("") { it.text } ?: ""
}

// Renderers
data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn>?,
    val thumbnail: MusicThumbnail?,
    val navigationEndpoint: NavigationEndpoint?,
    @SerializedName("videoId") val videoId: String?,
    val menu: Menu?
)

data class FlexColumn(
    val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer?
)

data class MusicResponsiveListItemFlexColumnRenderer(
    val text: YTRuns?
)

data class MusicThumbnail(
    val musicThumbnailRenderer: MusicThumbnailRenderer?
)

data class MusicThumbnailRenderer(
    val thumbnail: YTThumbnails?
)

data class MusicTwoRowItemRenderer(
    val title: YTRuns?,
    val subtitle: YTRuns?,
    val thumbnailRenderer: MusicThumbnailRenderer?,
    val navigationEndpoint: NavigationEndpoint?
)

data class VideoRenderer(
    @SerializedName("videoId") val videoId: String?,
    val thumbnail: YTThumbnails?,
    val title: YTRuns?,
    val longBylineText: YTRuns?
)

data class MusicCarouselShelfRenderer(
    val header: MusicCarouselShelfHeader?,
    val contents: List<ShelfContent>?
)

data class MusicCarouselShelfHeader(
    val musicCarouselShelfBasicHeaderRenderer: MusicCarouselShelfBasicHeaderRenderer?
)

data class MusicCarouselShelfBasicHeaderRenderer(
    val title: YTRuns?
)

data class MusicShelfRenderer(
    val title: YTRuns?,
    val contents: List<ShelfContent>?
)

data class ShelfContent(
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?,
    val musicTwoRowItemRenderer: MusicTwoRowItemRenderer?
)

data class NavigationEndpoint(
    @SerializedName("browseEndpoint") val browseEndpoint: BrowseEndpoint?,
    @SerializedName("watchEndpoint") val watchEndpoint: WatchEndpoint?
)

data class BrowseEndpoint(
    @SerializedName("browseId") val browseId: String?
)

data class WatchEndpoint(
    @SerializedName("videoId") val videoId: String?
)

data class Menu(
    val items: List<MenuItem>?
)

data class MenuItem(
    val menuNavigationItemRenderer: MenuNavigationItemRenderer?,
    val menuServiceItemRenderer: MenuServiceItemRenderer?
)

data class MenuNavigationItemRenderer(
    val navigationEndpoint: NavigationEndpoint?
)

data class MenuServiceItemRenderer(
    val serviceEndpoint: ServiceEndpoint?
)

data class ServiceEndpoint(
    val queueAddEndpoint: QueueAddEndpoint?
)

data class QueueAddEndpoint(
    val queueTarget: QueueTarget?
)

data class QueueTarget(
    @SerializedName("videoId") val videoId: String?
)

// Response Wrappers
data class YTSearchResponse(
    val contents: SearchContents?
)

data class SearchContents(
    val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer?
)

data class TabbedSearchResultsRenderer(
    val tabs: List<YTTab>?
)

data class YTTab(
    val tabRenderer: TabRenderer?
)

data class TabRenderer(
    val content: TabContent?
)

data class TabContent(
    val sectionListRenderer: SectionListRenderer?
)

data class SectionListRenderer(
    val contents: List<SectionContent>?
)

data class SectionContent(
    val musicShelfRenderer: MusicShelfRenderer?,
    val itemSectionRenderer: ItemSectionRenderer?
)

data class ItemSectionRenderer(
    val contents: List<ItemSectionContent>?
)

data class ItemSectionContent(
    val videoRenderer: VideoRenderer?
)

data class YTBrowseResponse(
    val contents: BrowseContents?
)

data class BrowseContents(
    val singleColumnBrowseResultsRenderer: SingleColumnBrowseResultsRenderer?
)

data class SingleColumnBrowseResultsRenderer(
    val tabs: List<YTTab>?
)

// Player Response
data class YTPlayerResponse(
    val playabilityStatus: PlayabilityStatus?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?
)

data class PlayabilityStatus(
    val status: String?,
    val reason: String?
)

data class StreamingData(
    val adaptiveFormats: List<AdaptiveFormat>?
)

data class AdaptiveFormat(
    val itag: Int?,
    val url: String?,
    val mimeType: String?,
    val bitrate: Long?,
    val contentLength: String?,
    val lastModified: String?,
    val signatureCipher: String?
)

data class VideoDetails(
    val videoId: String?,
    val title: String?,
    val lengthSeconds: String?,
    val channelId: String?,
    val author: String?,
    val thumbnail: YTThumbnails?
)

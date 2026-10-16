/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.dashboardfeature.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemSupportingContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.wrap.WrapAsyncImage
import eu.europa.ec.uilogic.component.wrap.WrapIcon

/**
 * Width:height ratio measured off a reference bank-card-style credential wallet screenshot
 * (~366x226 px for a single card), i.e. close to a physical payment card.
 */
internal const val CREDENTIAL_CARD_ASPECT_RATIO = 1.6f
private val CREDENTIAL_CARD_SHAPE = RoundedCornerShape(20.dp)
private val CREDENTIAL_CARD_PADDING = 20.dp

/**
 * Smaller than [CREDENTIAL_CARD_PADDING] on purpose: when this card is only "peeking" behind
 * another one in [CredentialStack], the visible strip is capped at a fixed height, so trimming
 * the header's top inset (and see the title's [MaterialTheme.typography.titleSmall] style below)
 * leaves enough room for the category/program-name line to stay inside that strip instead of
 * being cut off by the card stacked on top of it.
 */
private val CREDENTIAL_CARD_HEADER_TOP_PADDING = 12.dp
private val LEADING_LOGO_SIZE = 28.dp
private val BADGE_SIZE = 24.dp

/**
 * A large, bank-card-style credential tile: a diagonal metallic gradient background (lighter at
 * the top-left, darker at the bottom-right) resolved via [toCredentialMetallicGradient] from the
 * document's type and its [positionIndex] among all the user's documents, with white
 * text/iconography on top, matching the wallet's existing typography. Reads its content from the
 * same [ListItemDataUi] already built for the documents list so no new fields are needed on
 * [eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi].
 */
@Composable
fun CredentialCard(
    item: ListItemDataUi,
    documentIdentifier: DocumentIdentifier,
    category: String,
    positionIndex: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val (lightColor, darkColor) = documentIdentifier.toCredentialMetallicGradient(positionIndex)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CREDENTIAL_CARD_ASPECT_RATIO)
            .clip(CREDENTIAL_CARD_SHAPE)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(lightColor, darkColor),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(
                start = CREDENTIAL_CARD_PADDING,
                end = CREDENTIAL_CARD_PADDING,
                top = CREDENTIAL_CARD_HEADER_TOP_PADDING,
                bottom = CREDENTIAL_CARD_PADDING,
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (val leading = item.leadingContentData) {
                    is ListItemLeadingContentDataUi.AsyncImage -> WrapAsyncImage(
                        source = leading.imageUrl,
                        modifier = Modifier
                            .size(LEADING_LOGO_SIZE)
                            .clip(RoundedCornerShape(6.dp)),
                        contentDescription = leading.contentDescription,
                        error = leading.errorImage,
                        placeholder = leading.placeholderImage,
                    )

                    is ListItemLeadingContentDataUi.Icon -> WrapIcon(
                        iconData = leading.iconData,
                        customTint = Color.White,
                        modifier = Modifier.size(LEADING_LOGO_SIZE),
                    )

                    else -> {}
                }

                // e.g. "Academic Credential (SD-JWT VC Compact)" -> the "(...)" part always
                // starts its own line, instead of wrapping wherever it happens to run out of
                // width.
                val title = (item.mainContentData as? ListItemMainContentDataUi.Text)?.text
                    .orEmpty()
                    .replaceFirst(" (", "\n(")
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = category,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (item.trailingContentData as? ListItemTrailingContentDataUi.TextWithIcon)?.let { trailing ->
                    Text(
                        text = trailing.text,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(BADGE_SIZE)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    WrapIcon(
                        iconData = AppIcons.Verified,
                        customTint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            item.overlineText?.let { overline ->
                Text(
                    text = overline,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            (item.supportingContentData as? ListItemSupportingContentDataUi.Text)?.let { supporting ->
                Text(
                    text = supporting.text,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

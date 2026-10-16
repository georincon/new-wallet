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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapCard
import eu.europa.ec.uilogic.component.wrap.shadowsAtElevation2

/**
 * Shown when there is no PID issued yet, so [UserBadge] always has a name/initials to render
 * instead of falling back to a generic icon+label.
 */
private const val FALLBACK_USER_FIRST_NAME = "Geovani"

/**
 * Static initials shown in the [UserAvatar] of screens (Credenciales, Conexiones) that don't
 * bind their header to the active PID's real name/initials - only Home does that binding.
 */
const val FALLBACK_USER_INITIALS = "GR"

/**
 * White, full-width app-bar-style container used to group the top-of-screen controls (menu
 * icon, profile badge, search bar, view toggle, etc.), shared by Home, Credenciales and
 * Conexiones. Spans edge-to-edge (square top corners, rounded bottom corners only) with a
 * pronounced shadow along its bottom edge, separating it from the scrollable content below.
 */
private val HeaderCardBottomCornerRadius = 20.dp

@Composable
fun HeaderCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    WrapCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            bottomStart = HeaderCardBottomCornerRadius,
            bottomEnd = HeaderCardBottomCornerRadius,
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        shadows = shadowsAtElevation2,
    ) {
        content()
    }
}

/**
 * Circle avatar showing the user's initials (real PID given name initial, or the fallback
 * initials when no PID has been issued yet).
 */
@Composable
fun UserAvatar(
    initials: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Avatar + first-name row, used in the Home header. The avatar's initials stay the static
 * "GR" (matching the Credenciales/Conexiones headers), while the name label shows the real PID
 * given name when available, falling back to "Geovani" when no PID has been issued yet.
 */
@Composable
fun UserBadge(
    userFirstName: String,
    modifier: Modifier = Modifier,
) {
    val displayFirstName = userFirstName.ifBlank { FALLBACK_USER_FIRST_NAME }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(initials = FALLBACK_USER_INITIALS)
        Text(
            text = displayFirstName,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

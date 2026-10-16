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

package eu.europa.ec.dashboardfeature.ui.documents.list.model

import eu.europa.ec.businesslogic.validator.model.FilterableItemPayload
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.uilogic.component.ListItemDataUi

data class DocumentUi(
    val documentIssuanceState: DocumentIssuanceStateUi,
    val uiData: ListItemDataUi,
    val documentIdentifier: DocumentIdentifier,
    val documentCategory: DocumentCategory,
    /**
     * Label to show instead of [documentCategory]'s generic name (e.g. "OTROS") on the
     * credential card, when a more specific one can be read off the document's own claims —
     * e.g. an academic credential's program/degree name. Null falls back to the category name.
     */
    val categoryOverride: String? = null,
    /**
     * This document's rank in the STABLE default order (category, then source order) — used only
     * to pick its metallic gradient color (see [eu.europa.ec.dashboardfeature.ui.component.toCredentialMetallicGradient]),
     * so its color never changes when the user drags it elsewhere in the display order.
     */
    val colorIndex: Int = 0,
) : FilterableItemPayload
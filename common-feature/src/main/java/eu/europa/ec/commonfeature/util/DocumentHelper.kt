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

package eu.europa.ec.commonfeature.util

import eu.europa.ec.businesslogic.extension.decodeFromBase64ToString
import eu.europa.ec.businesslogic.extension.encodeToBase64String
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.businesslogic.util.safeLet
import eu.europa.ec.businesslogic.util.toDateFormatted
import eu.europa.ec.corelogic.extension.getLocalizedClaimName
import eu.europa.ec.corelogic.extension.identifierString
import eu.europa.ec.corelogic.extension.removeEmptyGroups
import eu.europa.ec.corelogic.extension.sortRecursivelyBy
import eu.europa.ec.corelogic.extension.toClaimPathSegment
import eu.europa.ec.corelogic.model.ClaimDomain
import eu.europa.ec.corelogic.model.ClaimPathDomain
import eu.europa.ec.corelogic.model.ClaimPathSegment
import eu.europa.ec.corelogic.model.ClaimType
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.DocumentClaim
import eu.europa.ec.eudi.wallet.document.format.MsoMdocClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.uuid.ExperimentalUuidApi

fun extractValueFromDocumentOrEmpty(
    document: IssuedDocument,
    key: String,
): String {
    return document.data.claims
        .firstOrNull { it.identifierString == key }
        ?.value
        ?.toString()
        ?: ""
}

/**
 * Claim keys tried, in order, to tell apart two documents of the exact same credential type that
 * describe genuinely different things (e.g. two academic credentials for two different degree
 * programs) from an accidental duplicate of the very same one. The first key present on the
 * document (non-blank) is used as its "identity" for comparison purposes.
 */
private val DUPLICATE_DISTINGUISHING_CLAIM_KEYS = listOf("program_code")

/**
 * Whether [this] and [other] describe the SAME credential — same type, and (for types with a
 * known distinguishing claim, see [DUPLICATE_DISTINGUISHING_CLAIM_KEYS]) the same "identity"
 * value — rather than merely the same credential *type*. A person can legitimately hold several
 * documents of one type (e.g. two different degree programs), so type alone is never enough to
 * call something a duplicate; PID documents are never flagged as duplicates by this check.
 */
fun IssuedDocument.isDuplicateOf(other: IssuedDocument): Boolean {
    if (this.id == other.id) return false

    val thisIdentifier = this.toDocumentIdentifier()
    if (thisIdentifier != other.toDocumentIdentifier()) return false
    if (thisIdentifier !is DocumentIdentifier.OTHER) return false

    val distinguishingKey = DUPLICATE_DISTINGUISHING_CLAIM_KEYS.firstOrNull { key ->
        extractValueFromDocumentOrEmpty(this, key).isNotBlank()
    }

    return if (distinguishingKey != null) {
        extractValueFromDocumentOrEmpty(this, distinguishingKey) ==
            extractValueFromDocumentOrEmpty(other, distinguishingKey)
    } else {
        // No known distinguishing claim for this credential type: fall back to comparing every
        // top-level claim value for exact equality, as a conservative safety net.
        val theseClaims = this.data.claims.associate { it.identifierString to it.value?.toString().orEmpty() }
        val otherClaims = other.data.claims.associate { it.identifierString to it.value?.toString().orEmpty() }
        theseClaims == otherClaims
    }
}

fun keyIsUserImage(key: String): Boolean {
    val listOfUserImageKeys = DocumentJsonKeys.BASE64_USER_IMAGE_KEYS
    return listOfUserImageKeys.contains(key)
}

fun keyIsSignature(key: String): Boolean {
    return key == DocumentJsonKeys.SIGNATURE
}

private fun keyIsUserPseudonym(key: String): Boolean {
    return key == DocumentJsonKeys.USER_PSEUDONYM
}

private fun keyIsGender(key: String): Boolean {
    val listOfGenderKeys = DocumentJsonKeys.GENDER_KEYS
    return listOfGenderKeys.contains(key)
}

private fun getGenderValue(value: String, resourceProvider: ResourceProvider): String =
    when (value) {
        "0" -> {
            resourceProvider.getString(R.string.request_gender_not_known)
        }

        "1" -> {
            resourceProvider.getString(R.string.request_gender_male)
        }

        "2" -> {
            resourceProvider.getString(R.string.request_gender_female)
        }

        "9" -> {
            resourceProvider.getString(R.string.request_gender_not_applicable)
        }

        else -> {
            value
        }
    }

/**
 * Local Spanish translations for well-known PID/mDL claim identifiers.
 *
 * The credential issuer only supplies an "en" locale entry in its claim display
 * metadata, so [eu.europa.ec.corelogic.extension.getLocalizedClaimName] can never
 * match the device's Spanish locale and silently falls back to that English text.
 * This table is checked first so known claims are always shown in Spanish; claims
 * not present here (e.g. from other issuers/document types) keep falling back to
 * whatever the issuer metadata provides.
 */
private val LOCAL_CLAIM_LABELS: Map<String, Int> = mapOf(
    DocumentJsonKeys.LAST_NAME to R.string.document_claim_family_name,
    DocumentJsonKeys.FIRST_NAME to R.string.document_claim_given_name,
    "birth_family_name" to R.string.document_claim_birth_family_name,
    "birth_given_name" to R.string.document_claim_birth_given_name,
    "birth_date" to R.string.document_claim_birth_date,
    "birthdate" to R.string.document_claim_birthdate,
    "age_over_18" to R.string.document_claim_age_over_18,
    "age_in_years" to R.string.document_claim_age_in_years,
    "age_birth_year" to R.string.document_claim_age_birth_year,
    "age_over_65" to R.string.document_claim_age_over_65,
    "birth_place" to R.string.document_claim_place_of_birth,
    "birth_city" to R.string.document_claim_birth_city,
    "nationality" to R.string.document_claim_nationality,
    "nationalities" to R.string.document_claim_nationalities,
    "gender" to R.string.document_claim_gender,
    "sex" to R.string.document_claim_sex,
    DocumentJsonKeys.PORTRAIT to R.string.document_claim_portrait,
    DocumentJsonKeys.PICTURE to R.string.document_claim_picture,
    DocumentJsonKeys.SIGNATURE to R.string.document_claim_signature_usual_mark,
    "place_of_birth" to R.string.document_claim_place_of_birth,
    "locality" to R.string.document_claim_locality,
    "region" to R.string.document_claim_region,
    "country" to R.string.document_claim_country,
    "address" to R.string.document_claim_address,
    "street_address" to R.string.document_claim_street_address,
    "formatted" to R.string.document_claim_formatted,
    "postal_code" to R.string.document_claim_postal_code,
    "resident_address" to R.string.document_claim_resident_address,
    "resident_country" to R.string.document_claim_resident_country,
    "resident_state" to R.string.document_claim_resident_state,
    "resident_city" to R.string.document_claim_resident_city,
    "resident_postal_code" to R.string.document_claim_resident_postal_code,
    "resident_street" to R.string.document_claim_resident_street,
    "resident_house_number" to R.string.document_claim_resident_house_number,
    "document_number" to R.string.document_claim_document_number,
    "administrative_number" to R.string.document_claim_administrative_number,
    "personal_administrative_number" to R.string.document_claim_personal_administrative_number,
    "issuing_country" to R.string.document_claim_issuing_country,
    "issuing_authority" to R.string.document_claim_issuing_authority,
    "issuing_jurisdiction" to R.string.document_claim_issuing_jurisdiction,
    "issuance_date" to R.string.document_claim_issuance_date,
    "date_of_issuance" to R.string.document_claim_issuance_date,
    DocumentJsonKeys.EXPIRY_DATE to R.string.document_claim_expiry_date,
    "date_of_expiry" to R.string.document_claim_expiry_date,
    "email_address" to R.string.document_claim_email_address,
    "email" to R.string.document_claim_email,
    "phone_number" to R.string.document_claim_phone_number,
    "driving_privileges" to R.string.document_claim_driving_privileges,
    "un_distinguishing_sign" to R.string.document_claim_un_distinguishing_sign,
    DocumentJsonKeys.USER_PSEUDONYM to R.string.document_claim_user_pseudonym,
    "attestation_legal_category" to R.string.document_claim_attestation_legal_category,

    // Academic credential claims (e.g. "Academic Credential"), keyed by the exact claim paths
    // confirmed off this issuer's own credential metadata (urn:eudi:academic:credential:1).
    "academic_level" to R.string.document_claim_academic_level,
    "program_code" to R.string.document_claim_academic_program_code,
    "program_name" to R.string.document_claim_academic_program_name,
    "academic_status" to R.string.document_claim_academic_status,
    "academic_unit" to R.string.document_claim_academic_unit,
    "accumulated_average" to R.string.document_claim_accumulated_average,
    "approved_credits" to R.string.document_claim_approved_credits,
    "awarded_title" to R.string.document_claim_awarded_title,
    "enrollment_date" to R.string.document_claim_enrollment_date,
    "faculty" to R.string.document_claim_faculty,
    "graduation_date" to R.string.document_claim_graduation_date,
    "identification_type" to R.string.document_claim_identification_type,
    "issuing_institution_name" to R.string.document_claim_issuing_institution_name,
    "duration_semesters" to R.string.document_claim_program_duration_semesters,
    "program_modality" to R.string.document_claim_program_modality,
    "semesters_completed" to R.string.document_claim_semesters_completed,
    "student_id" to R.string.document_claim_student_identification_number,
    "total_credits" to R.string.document_claim_total_credits,
)

fun getReadableNameFromIdentifier(
    claimMetaData: IssuerMetadata.Claim?,
    userLocale: Locale,
    fallback: String,
    resourceProvider: ResourceProvider,
): String {
    LOCAL_CLAIM_LABELS[fallback]?.let { resId ->
        return resourceProvider.getString(resId)
    }
    return claimMetaData
        ?.display.getLocalizedClaimName(
            userLocale = userLocale,
            fallback = fallback
        )
}

fun createKeyValue(
    item: Any,
    groupKey: String,
    childKey: String = "",
    disclosurePath: ClaimPathDomain,
    resourceProvider: ResourceProvider,
    uuidProvider: UuidProvider,
    claimMetaData: IssuerMetadata.Claim?,
    allItems: MutableList<ClaimDomain>,
) {

    @OptIn(ExperimentalUuidApi::class)
    fun addFlatOrGroupedChildren(
        allItems: MutableList<ClaimDomain>,
        children: List<ClaimDomain>,
        groupKey: String,
        displayTitle: String,
        predicate: () -> Boolean
    ) {

        val groupIsAlreadyPresent = children
            .filterIsInstance<ClaimDomain.Group>()
            .any { it.key == groupKey }

        if (predicate() && !groupIsAlreadyPresent) {
            allItems.add(
                ClaimDomain.Group(
                    key = groupKey,
                    displayTitle = displayTitle,
                    // the path on a UI-only group is just a unique id, never matched against a
                    // real disclosure path
                    path = ClaimPathDomain.forUiGroup(
                        groupId = uuidProvider.provideUuid(),
                        type = disclosurePath.type,
                    ),
                    items = children
                )
            )
        } else {
            allItems.addAll(children)
        }
    }

    when (item) {

        is Map<*, *> -> {

            val children: MutableList<ClaimDomain> = mutableListOf()
            val childKeys: MutableList<String> = mutableListOf()

            item.forEach { (key, value) ->
                safeLet(key as? String, value) { key, value ->

                    val newGroupKey = if (value is Collection<*>) key else groupKey
                    val newChildKey = if (value is Collection<*>) "" else key

                    childKeys.add(newChildKey)

                    createKeyValue(
                        item = value,
                        groupKey = newGroupKey,
                        childKey = newChildKey,
                        disclosurePath = disclosurePath,
                        resourceProvider = resourceProvider,
                        uuidProvider = uuidProvider,
                        claimMetaData = null,
                        allItems = children
                    )
                }
            }

            addFlatOrGroupedChildren(
                allItems = allItems,
                children = children,
                groupKey = groupKey,
                displayTitle = getReadableNameFromIdentifier(
                    claimMetaData = claimMetaData,
                    userLocale = resourceProvider.getLocale(),
                    fallback = groupKey,
                    resourceProvider = resourceProvider,
                )
            ) {
                childKeys.none { it.isEmpty() }
            }
        }

        is Collection<*> -> {

            val groupedChildren: MutableList<ClaimDomain> = mutableListOf()
            val isMultiElement = item.size > 1
            val isRecordLevel = childKey.isEmpty()

            item.forEachIndexed { index, value ->
                value?.let {

                    val entryChildren: MutableList<ClaimDomain> = mutableListOf()

                    createKeyValue(
                        item = it,
                        groupKey = groupKey,
                        disclosurePath = disclosurePath,
                        resourceProvider = resourceProvider,
                        uuidProvider = uuidProvider,
                        claimMetaData = claimMetaData,
                        allItems = entryChildren
                    )

                    val shouldCreateSubGroup = isMultiElement
                            && isRecordLevel
                            && entryChildren.size > 1
                            && entryChildren.none { child ->
                        child is ClaimDomain.Group && child.key == groupKey
                    }

                    if (shouldCreateSubGroup) {

                        val position = index + 1

                        groupedChildren.add(
                            ClaimDomain.Group(
                                key = "$groupKey-$position",
                                displayTitle = "${
                                    getReadableNameFromIdentifier(
                                        claimMetaData = claimMetaData,
                                        userLocale = resourceProvider.getLocale(),
                                        fallback = groupKey,
                                        resourceProvider = resourceProvider,
                                    )
                                } $position",
                                // UUID-only path for a UI sub-group; never matched against a
                                // real disclosure path
                                path = ClaimPathDomain.forUiGroup(
                                    groupId = uuidProvider.provideUuid(),
                                    type = disclosurePath.type,
                                ),
                                items = entryChildren
                            )
                        )
                    } else {
                        groupedChildren.addAll(entryChildren)
                    }
                }
            }

            addFlatOrGroupedChildren(
                allItems = allItems,
                children = groupedChildren,
                groupKey = groupKey,
                displayTitle = getReadableNameFromIdentifier(
                    claimMetaData = claimMetaData,
                    userLocale = resourceProvider.getLocale(),
                    fallback = groupKey,
                    resourceProvider = resourceProvider,
                )
            ) {
                childKey.isEmpty()
            }
        }

        else -> {

            val base64Image = (item as? ByteArray)?.encodeToBase64String()

            val date: String? = (item as? String)?.toDateFormatted()
                ?: (item as? LocalDate)?.toDateFormatted()

            val formattedValue = when {
                base64Image != null -> base64Image
                keyIsGender(groupKey) -> getGenderValue(item.toString(), resourceProvider)
                keyIsUserPseudonym(groupKey) -> item.toString().decodeFromBase64ToString()
                date != null -> date
                item is Boolean -> resourceProvider.getString(
                    resId = if (item)
                        R.string.document_details_boolean_item_true_readable_value
                    else
                        R.string.document_details_boolean_item_false_readable_value
                )

                else -> item.toString()
            }

            allItems.add(
                ClaimDomain.Primitive(
                    key = childKey.ifEmpty { groupKey },
                    displayTitle = childKey.ifEmpty {
                        getReadableNameFromIdentifier(
                            claimMetaData = claimMetaData,
                            userLocale = resourceProvider.getLocale(),
                            fallback = groupKey,
                            resourceProvider = resourceProvider,
                        )
                    },
                    path = disclosurePath,
                    isRequired = false,
                    value = formattedValue
                )
            )
        }
    }
}

fun documentHasExpired(
    documentExpirationDate: Instant,
    currentDate: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    return runCatching {
        // Convert Instant to LocalDate using the provided ZoneId
        val localDateOfDocumentExpiration = documentExpirationDate
            .atZone(zoneId)
            .toLocalDate()

        // Check if the current date is after the document expiration date
        currentDate.isAfter(localDateOfDocumentExpiration)
    }.getOrElse {
        // Default to false in case of any exception
        false
    }
}

private fun insertPath(
    tree: List<ClaimDomain>,
    path: ClaimPathDomain,
    disclosurePath: ClaimPathDomain,
    claims: List<DocumentClaim>,
    resourceProvider: ResourceProvider,
    uuidProvider: UuidProvider,
): List<ClaimDomain> {
    if (path.segments.isEmpty()) return tree

    val userLocale = resourceProvider.getLocale()

    val key = path.segments.first()
    // segment as a string, for the String-keyed node/claim lookups below
    val keyString: String = key.toString()

    val existingNode = tree.find {
        when (val type = disclosurePath.type) {
            is ClaimType.MsoMdoc -> {
                it.key == keyString && it.nameSpace == type.namespace
            }

            is ClaimType.SdJwtVc -> {
                it.key == keyString
            }
        }
    }

    val currentClaim: DocumentClaim? =
        when (val type = disclosurePath.type) {
            is ClaimType.MsoMdoc -> {
                claims
                    .filterIsInstance<MsoMdocClaim>()
                    .find { it.dataElementName == keyString && it.nameSpace == type.namespace }
            }

            is ClaimType.SdJwtVc -> {
                claims.filterIsInstance<SdJwtVcClaim>()
                    .find { it.pathElement.toClaimPathSegment() == key }
            }
        }

    return if (path.segments.size == 1) {
        // Leaf node (Primitive or Nested Structure)
        if (existingNode == null && currentClaim != null) {
            currentClaim.value?.let { safeClaimValue ->
                val accumulatedClaims: MutableList<ClaimDomain> = mutableListOf()

                createKeyValue(
                    item = safeClaimValue,
                    groupKey = currentClaim.identifierString,
                    resourceProvider = resourceProvider,
                    uuidProvider = uuidProvider,
                    claimMetaData = currentClaim.issuerMetadata,
                    disclosurePath = disclosurePath,
                    allItems = accumulatedClaims,
                )

                tree + accumulatedClaims
            } ?: tree // No value to add (claim value is null), return unchanged
        } else {
            tree // Already exists or not available, return unchanged
        }
    } else {
        // Group node (Intermediate)
        val childClaims = (currentClaim as? SdJwtVcClaim)?.children ?: claims
        val updatedNode = if (existingNode is ClaimDomain.Group) {
            // Update existing group by inserting the next path segment into its items
            existingNode.copy(
                items = insertPath(
                    tree = existingNode.items,
                    path = path.copy(segments = path.segments.drop(1)),
                    disclosurePath = disclosurePath,
                    claims = childClaims,
                    resourceProvider = resourceProvider,
                    uuidProvider = uuidProvider,
                )
            )
        } else {
            // Create a new group and insert the next path segment
            ClaimDomain.Group(
                key = currentClaim?.identifierString ?: keyString,
                displayTitle = getReadableNameFromIdentifier(
                    claimMetaData = currentClaim?.issuerMetadata,
                    userLocale = userLocale,
                    fallback = currentClaim?.identifierString ?: keyString,
                    resourceProvider = resourceProvider,
                ),
                path = ClaimPathDomain(
                    segments = disclosurePath.segments.take((disclosurePath.segments.size - path.segments.size) + 1),
                    type = path.type
                ),
                items = insertPath(
                    tree = emptyList(),
                    path = path.copy(segments = path.segments.drop(1)),
                    disclosurePath = disclosurePath,
                    claims = childClaims,
                    resourceProvider = resourceProvider,
                    uuidProvider = uuidProvider,
                )
            )
        }

        // Replace or add the updated node
        tree.filter {
            when (val type = disclosurePath.type) {
                is ClaimType.MsoMdoc -> {
                    it.key != keyString || it.nameSpace != type.namespace
                }

                is ClaimType.SdJwtVc -> {
                    it.key != keyString
                }
            }
        } + updatedNode
    }
}

private fun ClaimPathDomain.isTechnicalClaim(): Boolean {
    val root = segments.firstOrNull() as? ClaimPathSegment.Key ?: return false
    return root.name in DocumentJsonKeys.TECHNICAL_CLAIM_KEYS
}

// Function to build the tree from a list of paths
fun transformPathsToDomainClaims(
    paths: List<ClaimPathDomain>,
    claims: List<DocumentClaim>,
    resourceProvider: ResourceProvider,
    uuidProvider: UuidProvider
): List<ClaimDomain> {
    return paths.filterNot { it.isTechnicalClaim() }
        .fold<ClaimPathDomain, List<ClaimDomain>>(initial = emptyList()) { acc, path ->
            insertPath(
                tree = acc,
                path = path,
                disclosurePath = path,
                claims = claims,
                resourceProvider = resourceProvider,
                uuidProvider = uuidProvider
            )
        }.removeEmptyGroups()
        .sortRecursivelyBy {
            it.displayTitle.lowercase()
        }
}
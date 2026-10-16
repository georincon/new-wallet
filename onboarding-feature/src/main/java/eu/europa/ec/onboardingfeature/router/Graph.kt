package eu.europa.ec.onboardingfeature.router

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.koin.compose.koinInject
import eu.europa.ec.onboardingfeature.data.PersonalInfoStore
import eu.europa.ec.onboardingfeature.ui.personalinfo.PersonalInfoScreen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import eu.europa.ec.onboardingfeature.data.OnboardingStatus
import eu.europa.ec.onboardingfeature.data.OnboardingStore
import eu.europa.ec.onboardingfeature.navigation.OnboardingNavHost
import eu.europa.ec.onboardingfeature.ui.OnboardingState
import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.corelogic.BuildConfig
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.OnboardingScreens

fun NavGraphBuilder.featureOnboardingGraph(navController: NavController) {
    navigation(
        startDestination = OnboardingScreens.Onboarding.screenRoute,
        route = ModuleRoute.OnboardingModule.route
    ) {
        composable(route = OnboardingScreens.Onboarding.screenRoute) {
            val context = LocalContext.current.applicationContext
            val personalInfoStore = koinInject<PersonalInfoStore>()
            val state = remember {
                OnboardingState(OnboardingStore(context), OnboardingStatus(context), personalInfoStore)
            }
            val scope = rememberCoroutineScope()
            // rememberSaveable: se ejecuta solo al entrar al flujo, no en recreaciones de la pantalla.
            rememberSaveable { state.discardStaleCaptures(); true }
            val uiSerializer = koinInject<UiSerializer>()
            OnboardingNavHost(
                state = state,
                issuerBaseUrl = BuildConfig.NW_ISSUER_URL,
                onFinished = { offerUri ->
                    val target = if (offerUri == null) DashboardScreens.Dashboard.screenRoute
                    else offerRoute(uiSerializer, offerUri)
                    navController.navigate(target) {
                        popUpTo(OnboardingScreens.Onboarding.screenRoute) { inclusive = true }
                    }
                }
            )
        }

        composable(route = OnboardingScreens.PersonalInfo.screenRoute) {
            val uiSerializer = koinInject<UiSerializer>()
            PersonalInfoScreen(
                onBack = { navController.popBackStack() },
                onOffer = { offerUri -> navController.navigate(offerRoute(uiSerializer, offerUri)) }
            )
        }
    }
}


/** Ruta de la pantalla de oferta de la wallet para la PID (mdoc + SD-JWT) ofrecida por la Registraduría. */
private fun offerRoute(uiSerializer: UiSerializer, offerUri: String): String =
    generateComposableNavigationLink(
        screen = IssuanceScreens.DocumentOffer,
        arguments = generateComposableArguments(
            mapOf(
                OfferUiConfig.serializedKeyName to uiSerializer.toBase64(
                    OfferUiConfig(
                        offerUri = offerUri,
                        onSuccessNavigation = ConfigNavigation(
                            navigationType = NavigationType.PushScreen(
                                screen = DashboardScreens.Dashboard,
                                popUpToScreen = IssuanceScreens.DocumentOffer
                            )
                        ),
                        onCancelNavigation = ConfigNavigation(
                            navigationType = NavigationType.PushScreen(
                                screen = DashboardScreens.Dashboard,
                                popUpToScreen = IssuanceScreens.DocumentOffer
                            )
                        )
                    ),
                    OfferUiConfig.Parser
                ).orEmpty()
            )
        )
    )

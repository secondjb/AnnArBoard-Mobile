package com.example.annarboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.annarboard.network.Constants
import com.example.annarboard.network.RetrofitClient
import com.example.annarboard.service.TrackingService
import com.example.annarboard.theme.LocalSettingsManager
import com.example.annarboard.theme.SettingsManager
import com.example.annarboard.ui.*
import com.example.annarboard.ui.theme.AnnArBoardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.os.Build

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val settingsManager = SettingsManager(this)

        handleIntent(intent)

        setContent {
            CompositionLocalProvider(LocalSettingsManager provides settingsManager) {
                val currentTheme = settingsManager.currentSettings.value.appTheme
                AnnArBoardTheme(appTheme = currentTheme) {
                    MainScreen(
                        onStartTrackingService = { origin, dest, busId ->
                            startTrackingService(origin, dest, busId)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data?.scheme == "annarboard") {
            Log.d("MainActivity", "Deep link triggered: ${intent.data}")
            startTrackingService("cctc", "pierpont")
        }
    }

    private fun startTrackingService(origin: String, destination: String, busId: String? = null) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val serviceIntent = Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_TRACKING
            putExtra(TrackingService.EXTRA_ORIGIN_HUB, origin)
            putExtra(TrackingService.EXTRA_DESTINATION_HUB, destination)
            busId?.let { putExtra(TrackingService.EXTRA_BUS_ID, it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}



@Composable
fun RouteHeader(
    departure: String, 
    destination: String, 
    onSwap: () -> Unit,
    onEditDeparture: () -> Unit,
    onEditDestination: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .background(Color.Black.copy(alpha = 0.02f))
            .drawBehind {
                drawLine(Color.LightGray.copy(alpha=0.5f), Offset(0f, 0f), Offset(size.width, 0f), 2f)
                drawLine(Color.LightGray.copy(alpha=0.5f), Offset(0f, size.height), Offset(size.width, size.height), 2f)
            }
            .padding(vertical = 16.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = departure,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable { onEditDeparture() }
        )
        
        var isSwapped by remember { mutableStateOf(false) }
        val rotation by animateFloatAsState(targetValue = if (isSwapped) 180f else 0f)

        IconButton(onClick = { 
            isSwapped = !isSwapped
            onSwap() 
        }) {
            Icon(Icons.Default.SwapVert, contentDescription = "Swap", modifier = Modifier.size(36.dp).graphicsLayer { rotationZ = rotation })
        }
        Text(
            text = destination,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable { onEditDestination() }
        )
    }
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun isOnMobileData(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onStartTrackingService: (String, String, String?) -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettingsManager.current.currentSettings.value

    var alerts by remember { mutableStateOf<List<BusAlert>>(emptyList()) }
    var departures by remember { mutableStateOf<List<DepartureInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pausedForMobileData by remember { mutableStateOf(false) }
    var tickingTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(Unit) {
        while(true) {
            tickingTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    var isDepartureModalOpen by remember { mutableStateOf(false) }
    var isDestinationModalOpen by remember { mutableStateOf(false) }

    var originHubIndex by remember { mutableIntStateOf(0) }
    var destHubIndex by remember { mutableIntStateOf(1) }
    
    val originHub = Constants.HUBS[originHubIndex]
    val destHub = Constants.HUBS[destHubIndex]

    LaunchedEffect(originHub, destHub, settings.updateFrequencyMBus) {
        while (true) {
            if (!settings.mbusEnabled) {
                alerts = emptyList()
                departures = emptyList()
                pausedForMobileData = false
            } else if (!settings.updateOnMobileData && isOnMobileData(context)) {
                pausedForMobileData = true
            } else {
                pausedForMobileData = false
                var loadingJob: kotlinx.coroutines.Job? = null
                if (alerts.isEmpty() && departures.isEmpty()) {
                    loadingJob = launch {
                        delay(5000)
                        loading = true
                    }
                }
                try {
                    val response = RetrofitClient.instance.getMBusPredictions(originHub.stopIds.joinToString(","))
                    val prdList = response.bustimeResponse?.prd ?: emptyList()
                    
                    val originKey = originHub.key.lowercase()
                    val destKey = destHub.key.lowercase()
                    
                    val fromCentral = originKey in listOf("cctc", "union")
                    val toNorth = destKey in listOf("pierpont", "fxb", "bursley", "art")
                    val fromNorth = originKey in listOf("pierpont", "fxb", "bursley", "art")
                    val toCentral = destKey in listOf("cctc", "union")
                    
                    var allowedRoutes = emptyList<String>()
                    if (fromCentral && toNorth) allowedRoutes = Constants.ROUTE_MAP["central-to-north"] ?: emptyList()
                    else if (fromNorth && toCentral) allowedRoutes = Constants.ROUTE_MAP["north-to-central"] ?: emptyList()
                    else if (destKey == "hospital") {
                        allowedRoutes = if (fromNorth) Constants.ROUTE_MAP["north-to-hospital"] ?: emptyList() 
                                        else Constants.ROUTE_MAP["central-to-hospital"] ?: emptyList()
                    }

                    val filteredAlerts = if (allowedRoutes.isNotEmpty()) {
                        prdList.filter { allowedRoutes.contains(it.rt) }
                    } else {
                        prdList.filter { p ->
                            val des = p.des.lowercase()
                            destHub.keywords.any { kw -> des.contains(kw.lowercase()) }
                        }
                    }

                    alerts = filteredAlerts.map {
                        BusAlert(
                            leaveInMinutes = if (it.prdctdn == "DUE") 0 else it.prdctdn.toIntOrNull() ?: 0,
                            bus = it.rt,
                            location = it.stpnm,
                            system = "MBus"
                        )
                    }.sortedBy { it.leaveInMinutes }.take(settings.maxBusesActionBoard)

                    departures = prdList.map {
                        DepartureInfo(
                            time = it.prdtm.split(" ").lastOrNull() ?: it.prdtm,
                            busLine = it.rt,
                            location = it.stpnm,
                            system = "MBus"
                        )
                    }.take(settings.maxBusesDepartureList)
                    
                    lastUpdateTime = System.currentTimeMillis()

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    loadingJob?.cancel()
                    loading = false
                }
            }
            delay(if (settings.isSplitFrequency) (settings.updateFrequencyMBus * 1000).toLong() else (settings.updateFrequency * 1000).toLong())
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    if (settings.showMainLogo) {
                        val gradientStart = MaterialTheme.colorScheme.primary
                        val gradientEnd = MaterialTheme.colorScheme.secondary

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = "Bus Logo",
                                tint = gradientStart,
                                modifier = Modifier.size(32.dp)
                            )

                            Text(
                                text = "Ann Ar-Board",
                                style = TextStyle(
                                    color = Color.Black, // Needs a solid color to act as a mask for the gradient
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                modifier = Modifier
                                    // 1. Isolate the drawing layer
                                    .graphicsLayer(alpha = 0.99f)
                                    // 2. Draw the text, then paint the gradient directly over top of it
                                    .drawWithCache {
                                        val brush = Brush.linearGradient(listOf(gradientStart, gradientEnd))
                                        onDrawWithContent {
                                            drawContent()
                                            drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
                                        }
                                    }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            RouteHeader(
                departure = originHub.name,
                destination = destHub.name,
                onSwap = { 
                    val temp = originHubIndex
                    originHubIndex = destHubIndex
                    destHubIndex = temp
                },
                onEditDeparture = { isDepartureModalOpen = true },
                onEditDestination = { isDestinationModalOpen = true }
            )
            
            val isStale = (tickingTime - lastUpdateTime) > 30000
            if ((isStale || pausedForMobileData) && settings.showGlobalStaleWarning && settings.mbusEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (pausedForMobileData) "Live updates are paused because you are on a cellular network. Connect to Wi-Fi or adjust your settings." else "Warning: MBus is currently out of sync and may be inaccurate.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            
            if (settings.showActionBoard) {
                ActionBoard(
                    alerts = alerts,
                    loading = loading,
                    onStartTracking = { alert ->
                        onStartTrackingService(originHub.key, destHub.key, alert.bus)
                    }
                )
            }

            if (settings.showUpcomingDepartures) {
                DepartureList(departures = departures, loading = loading)
            }
            
            if (showSettings) {
                SettingsSheet(onDismissRequest = { showSettings = false })
            }

            if (isDepartureModalOpen) {
                StopSelectorModal(
                    open = true,
                    onClose = { isDepartureModalOpen = false },
                    onSelect = { hub -> 
                        originHubIndex = Constants.HUBS.indexOf(hub).takeIf { it != -1 } ?: 0 
                    },
                    title = "Select Departure Hub",
                    currentLocation = originHub.name,
                    hubs = Constants.HUBS
                )
            }

            if (isDestinationModalOpen) {
                StopSelectorModal(
                    open = true,
                    onClose = { isDestinationModalOpen = false },
                    onSelect = { hub -> 
                        destHubIndex = Constants.HUBS.indexOf(hub).takeIf { it != -1 } ?: 1 
                    },
                    title = "Select Destination Hub",
                    currentLocation = destHub.name,
                    hubs = Constants.HUBS
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            SyncFooter(lastUpdateTime = lastUpdateTime, pausedForMobileData = pausedForMobileData)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
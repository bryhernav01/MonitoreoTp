package com.example.monitoreotp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(
                this,
                "Permisos concedidos. Puede iniciar el monitoreo.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this,
                "Se necesitan permisos de ubicación para el tracking",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(this)
        checkAndRequestPermissions()
        setContent {
            MaterialTheme {
                navController = rememberNavController()
                MonitoreoTPApp(navController = navController)
            }
        }
    }

    fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            Toast.makeText(
                this,
                "Todos los permisos están concedidos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startLocationService(context: Context, deviceId: String) {
        val serviceIntent = Intent(this, LocationService::class.java).apply {
            putExtra("EXTRA_DEVICE_ID", deviceId)
            putExtra("EXTRA_FROM_BOOT", false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoreoTPApp(
    navController: NavHostController
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    //colores
    val colorAzulOscuro = Color(0xFF001A7F)
    val colorRojo = Color(0xFFF02030)

    //controler de rutas
    val currentRoute =
        navController.currentBackStackEntryAsState().value?.destination?.route ?: "dashboard"

    val navItems = listOf(
        NavigationItem("dashboard", "Dashboard", Icons.Default.Dashboard),
        NavigationItem("mapa", "Mapa", Icons.Default.Map),
        NavigationItem("historial", "Historial", Icons.Default.History),
        NavigationItem("configuracion", "Configuración", Icons.Default.Settings),
        NavigationItem("ayuda", "Ayuda", Icons.Default.Help)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colorAzulOscuro,
                drawerContentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            colorRojo,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "MonitoreoTP",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Monitoreo de Flota",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "v2.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                navItems.forEach { item ->
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                item.icon, contentDescription = item.label,
                                tint = Color.White
                            )
                        },
                        label = { Text(item.label, color = Color.White) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = colorRojo.copy(alpha = 0.3f),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.White.copy(alpha = 0.7f),
                            selectedIconColor = Color.White,
                            unselectedIconColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Divider(color = Color.White.copy(alpha = 0.2f))

                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Default.Share, contentDescription = "Compartir",
                            tint = Color.White
                        )
                    },
                    label = { Text("Compartir", color = Color.White) },
                    selected = false,
                    onClick = { /* Compartir */ },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = colorRojo.copy(alpha = 0.3f),
                        unselectedContainerColor = Color.Transparent,
                        selectedTextColor = Color.White,
                        unselectedTextColor = Color.White.copy(alpha = 0.7f),
                        selectedIconColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.7f)
                    )
                )
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Default.Info, contentDescription = "Acerca de",
                            tint = Color.White
                        )
                    },
                    label = { Text("Acerca de", color = Color.White) },
                    selected = false,
                    onClick = { /* Acerca de */ },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = colorRojo.copy(alpha = 0.3f),
                        unselectedContainerColor = Color.Transparent,
                        selectedTextColor = Color.White,
                        unselectedTextColor = Color.White.copy(alpha = 0.7f),
                        selectedIconColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.7f)
                    )
                )
            }
        },
        content = {
            NavHost(
                navController = navController,
                startDestination = "dashboard"
            ) {
                composable("dashboard") {
                    MainScreen(
                        drawerState = drawerState,
                        scope = scope,
                        onNavigateToConfig = {
                            navController.navigate("configuracion")
                        }
                    )
                }
                composable("mapa") {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Mapa",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                composable("historial") {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Historial",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                composable("configuracion") {
                    ConfigScreen(
                        onBackPressed = {
                            navController.navigateUp()
                        }
                    )
                }
                composable("ayuda") {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ayuda",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    drawerState: DrawerState,
    scope: kotlinx.coroutines.CoroutineScope,
    onNavigateToConfig: () -> Unit
) {
    val context = LocalContext.current
    val configManager = ConfigManager.getInstance(context)
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var deviceId by remember { mutableStateOf(prefs.getString("device_id", "") ?: "") }

    var stopCodeInput by remember { mutableStateOf("") }
    var stopCodeError by remember { mutableStateOf("") }
    val stopCodeRequired = configManager.getStopCode()
    var isStartButtonEnabled by remember { mutableStateOf(true) }
    var showStopDialog by remember { mutableStateOf(false) }

    val validationState by remember(deviceId) {
        derivedStateOf {
            when {
                deviceId.isEmpty() -> Pair(false, "")
                deviceId.length != 15 -> Pair(false, "El IMEI debe tener exactamente 15 dígitos")
                !deviceId.matches(Regex("^\\d{15}$")) -> Pair(
                    false,
                    "El IMEI solo debe contener números"
                )

                else -> Pair(true, "IMEI válido (15 dígitos)")
            }
        }
    }

    var isServiceRunning by remember {
        mutableStateOf(isLocationServiceRunning(context))
    }

    var permissionsStatus by remember {
        mutableStateOf(hasAllPermissions(context))
    }

    var isBatteryOptimizationOff by remember {
        mutableStateOf(isBatteryOptimizationDisabled(context))
    }

    var locationsSent by remember {
        mutableStateOf(prefs.getInt("locations_sent", 0))
    }

    var apiStatus by remember {
        mutableStateOf(if (prefs.getBoolean("api_ok", false)) " Conectado" else "❌ Sin conexión")
    }

    var lastLocation by remember {
        mutableStateOf(
            if (prefs.getFloat("last_lat", 0f) != 0f) {
                " ${String.format("%.6f", prefs.getFloat("last_lat", 0f))}, ${
                    String.format(
                        "%.6f",
                        prefs.getFloat("last_lng", 0f)
                    )
                }"
            } else " Esperando ubicación..."
        )
    }

    fun refreshData() {
        val newLocations = prefs.getInt("locations_sent", 0)
        if (newLocations != locationsSent) {
            locationsSent = newLocations
            Log.d("MainScreen", "locationsSent actualizado: $newLocations")
        }

        val newApiOk = prefs.getBoolean("api_ok", false)
        val newApiStatus = if (newApiOk) " Conectado" else " Sin conexión"
        if (newApiStatus != apiStatus) {
            apiStatus = newApiStatus
            Log.d("MainScreen", "apiStatus actualizado: $newApiStatus")
        }

        val lat = prefs.getFloat("last_lat", 0f)
        val lng = prefs.getFloat("last_lng", 0f)
        if (lat != 0f && lng != 0f) {
            val newLocation = " ${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}"
            if (newLocation != lastLocation) {
                lastLocation = newLocation
                Log.d("MainScreen", "lastLocation actualizado: $newLocation")
            }
        }
    }

    val prefsListener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "locations_sent", "api_ok", "last_lat", "last_lng" -> {
                    refreshData()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        refreshData()
        Log.d("MainScreen", "📡 Listener registrado - locationsSent: $locationsSent")
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)

            val newLocations = prefs.getInt("locations_sent", 0)
            if (newLocations != locationsSent) {
                locationsSent = newLocations
                Log.d("MainScreen", "Forzado locationsSent: $newLocations")
            }

            val newApiOk = prefs.getBoolean("api_ok", false)
            val newApiStatus = if (newApiOk) " Conectado" else "Sin conexión"
            if (newApiStatus != apiStatus) {
                apiStatus = newApiStatus
            }

            val lat = prefs.getFloat("last_lat", 0f)
            val lng = prefs.getFloat("last_lng", 0f)
            if (lat != 0f && lng != 0f) {
                val newLocation = "${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}"
                if (newLocation != lastLocation) {
                    lastLocation = newLocation
                    Log.d("MainScreen", "Forzado lastLocation: $newLocation")
                }
            }

            val newServiceRunning = isLocationServiceRunning(context)
            if (newServiceRunning != isServiceRunning) {
                isServiceRunning = newServiceRunning
            }

            permissionsStatus = hasAllPermissions(context)
            isBatteryOptimizationOff = isBatteryOptimizationDisabled(context)
        }
    }

    val colorAzulOscuro = Color(0xFF001A7F)
    val colorRojo = Color(0xFFF02030)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MonitoreoTP",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Monitoreo en tiempo real",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menú", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        refreshData()
                        Toast.makeText(
                            context,
                            "Datos actualizados: $locationsSent ubicaciones",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refrescar",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onNavigateToConfig) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Configuración",
                            tint = Color.White
                        )
                    }
                    Badge(
                        containerColor = if (isServiceRunning) Color.Green else colorRojo,
                    ){
                        Text(if(isServiceRunning) "ON" else "OFF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorAzulOscuro,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ServiceStatusCard(
                    isServiceRunning = isServiceRunning,
                    locationsSent = locationsSent,
                    apiStatus = apiStatus,
                    lastLocation = lastLocation,
                    validationMessage = validationState.second,
                    deviceId = deviceId,
                    isStartButtonEnabled = isStartButtonEnabled,
                    onStartStopClick = {
                        if (isServiceRunning) {
                            showStopDialog = true
                        } else {
                            if (deviceId.isEmpty()) {
                                Toast.makeText(context, "Ingrese un IMEI", Toast.LENGTH_SHORT)
                                    .show()
                                return@ServiceStatusCard
                            }
                            if (deviceId.length != 15) {
                                Toast.makeText(
                                    context,
                                    "El IMEI debe tener 15 dígitos",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@ServiceStatusCard
                            }
                            if (!deviceId.matches(Regex("^\\d{15}$"))) {
                                Toast.makeText(
                                    context,
                                    "El IMEI solo debe contener números",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@ServiceStatusCard
                            }

                            isStartButtonEnabled = false
                            prefs.edit().putString("device_id", deviceId).apply()
                            prefs.edit().putBoolean("service_should_run", true).apply()
                            startLocationService(context, deviceId)
                            isServiceRunning = true
                            Toast.makeText(context, "Monitoreo iniciado", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                delay(2000)
                                isStartButtonEnabled = true
                            }
                        }
                    },
                    onRefreshClick = { refreshData() }
                )
            }
        }
    }

    // Diálogo de detención
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = {
                showStopDialog = false
                stopCodeInput = ""
                stopCodeError = ""
            },
            title = { Text("Detener Monitoreo") },
            text = {
                Column {
                    Text("Ingrese el código de seguridad para detener el monitoreo:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = stopCodeInput,
                        onValueChange = {
                            stopCodeInput = it
                            stopCodeError = ""
                        },
                        label = { Text("Código de seguridad") },
                        placeholder = { Text("Ingrese el código") },
                        isError = stopCodeError.isNotEmpty(),
                        supportingText = {
                            if (stopCodeError.isNotEmpty()) {
                                Text(
                                    text = stopCodeError,
                                    color = colorRojo
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (stopCodeInput == stopCodeRequired) {
                            stopLocationService(context)
                            prefs.edit().putBoolean("service_should_run", false).apply()
                            isServiceRunning = false
                            showStopDialog = false
                            stopCodeInput = ""
                            stopCodeError = ""
                            Toast.makeText(context, "Monitoreo detenido", Toast.LENGTH_SHORT).show()
                        } else {
                            stopCodeError = "Código incorrecto"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorRojo)
                ) {
                    Text("Detener", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showStopDialog = false
                        stopCodeInput = ""
                        stopCodeError = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorAzulOscuro)
                ) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }
}


fun isLocationServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (LocationService::class.java.name == service.service.className) {
            return true
        }
    }
    return false
}

@Composable
fun MetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A2E)
                )
            }
        }
    }
}

fun isBatteryOptimizationDisabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val packageName = context.packageName
        return !powerManager.isIgnoringBatteryOptimizations(packageName)
    }
    return true
}

fun startLocationService(context: Context, deviceId: String) {
    val serviceIntent = Intent(context, LocationService::class.java).apply {
        putExtra("EXTRA_DEVICE_ID", deviceId)
        putExtra("EXTRA_FROM_BOOT", false)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
    } else {
        context.startService(serviceIntent)
    }
}

fun stopLocationService(context: Context) {
    val serviceIntent = Intent(context, LocationService::class.java)
    context.stopService(serviceIntent)
}


@Composable
fun ServiceStatusCard(
    isServiceRunning: Boolean,
    locationsSent: Int,
    apiStatus: String,
    lastLocation: String,
    validationMessage: String,
    deviceId: String,
    isStartButtonEnabled: Boolean,
    onStartStopClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    val colorAzulOscuro = Color(0xFF001A7F)
    val colorRojo = Color(0xFFF02030)

    LaunchedEffect(locationsSent, lastLocation) {
        android.util.Log.d(
            "ServiceStatusCard",
            "Recomposición - locationsSent: $locationsSent, lastLocation: $lastLocation"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isServiceRunning)
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFE8F5E9),
                                Color.White
                            )
                        )
                    else
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFEBEE),
                                Color.White
                            )
                        )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header con estado y botón
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isServiceRunning)
                                    Color(0xFF4CAF50).copy(alpha = 0.15f)
                                else
                                    Color(0xFFF44336).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SatelliteAlt,
                            contentDescription = "Servicio",
                            tint = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Estado del Servicio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A1A2E)
                        )
                        Text(
                            text = if (isServiceRunning) "Monitoreo activo" else "Monitoreo detenido",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }

                Button(
                    onClick = onStartStopClick,
                    enabled = if (isServiceRunning) true else (deviceId.isNotEmpty() &&
                            deviceId.length == 15 &&
                            deviceId.matches(Regex("^\\d{15}$")) &&
                            isStartButtonEnabled),
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) Color(0xFFF44336) else Color(
                            0xFF4CAF50
                        )
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            if (isServiceRunning) "Detener" else "Iniciar",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Estado con indicador visual
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isServiceRunning)
                            Color(0xFF4CAF50).copy(alpha = 0.08f)
                        else
                            Color(0xFFF44336).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFF44336),
                            shape = CircleShape
                        )
                ) {
                    if (isServiceRunning) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    Color(0xFF4CAF50).copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Text(
                    text = if (isServiceRunning) "Servicio Activo" else "Servicio Detenido",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isServiceRunning) Color(0xFF2E7D32) else Color(0xFFC62828)
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isServiceRunning) {
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "● En vivo",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color(0xFFE0E0E0),
                thickness = 1.dp
            )

            // Métricas en grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    icon = if (apiStatus.contains("Conectado")) Icons.Default.Wifi else Icons.Default.WifiOff,
                    title = "API",
                    value = apiStatus,
                    color = if (apiStatus.contains("Conectado")) Color(0xFF4CAF50) else Color(
                        0xFFF44336
                    ),
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    icon = Icons.Default.LocationOn,
                    title = "Ubicaciones",
                    value = locationsSent.toString(),
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
            }

            // Última ubicación
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "GPS",
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Última ubicación: $lastLocation",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF616161)
                )
            }

            // Mensaje de validación IMEI
            if (validationMessage.isNotEmpty()) {
                Surface(
                    color = if (validationMessage.contains("✅"))
                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                    else
                        Color(0xFFF44336).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = validationMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (validationMessage.contains("✅")) Color(0xFF2E7D32) else Color(
                            0xFFC62828
                        ),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

fun hasAllPermissions(context: Context): Boolean {
    val fineLocation = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarseLocation = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    val foregroundServiceLocation =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    return fineLocation && coarseLocation && backgroundLocation &&
            foregroundServiceLocation && notifications
}

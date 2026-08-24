package com.example.monitoreotp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.net.URL
import java.util.concurrent.TimeUnit


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // Estados
    var deviceId by remember { mutableStateOf(prefs.getString("device_id", "") ?: "") }
    var isServiceRunning by remember { mutableStateOf(isLocationServiceRunning(context)) }
    var permissionsStatus by remember { mutableStateOf(hasAllPermissions(context)) }
    var isBatteryOptimizationOff by remember { mutableStateOf(isBatteryOptimizationDisabled(context)) }


    // Colores
    val colorAzulOscuro = Color(0xFF001A7F)
    val colorRojo = Color(0xFFF02030)
    val colorAzulMedio = Color(0xFF4A7FC1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configuración",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorAzulOscuro,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ========== SECCIÓN: DISPOSITIVO ==========
            ConfigCard(
                icon = Icons.Default.PhoneAndroid,
                title = "Dispositivo",
                subtitle = "Configuración del IMEI"
            ) {
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }
                        if (filtered.length <= 15) {
                            deviceId = filtered
                            prefs.edit().putString("device_id", filtered).apply()
                        }
                    },
                    label = { Text("IMEI (15 dígitos)") },
                    placeholder = { Text("Ingrese el IMEI del dispositivo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isServiceRunning,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorAzulOscuro,
                        unfocusedBorderColor = colorAzulOscuro.copy(alpha = 0.3f),
                        focusedLabelColor = colorAzulOscuro,
                        unfocusedLabelColor = colorAzulOscuro.copy(alpha = 0.6f),
                        cursorColor = colorAzulOscuro
                    ),
                    trailingIcon = {
                        when {
                            deviceId.isEmpty() -> Spacer(modifier = Modifier.size(24.dp))
                            deviceId.length == 15 && deviceId.matches(Regex("^\\d{15}$")) -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Válido",
                                    tint = Color(0xFF4CAF50)
                                )
                            }

                            deviceId.length == 15 -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Formato inválido",
                                    tint = Color(0xFFFFA500)
                                )
                            }

                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Inválido",
                                    tint = colorRojo
                                )
                            }
                        }
                    },
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    deviceId.isEmpty() -> "Ingrese el IMEI"
                                    deviceId.length == 15 && deviceId.matches(Regex("^\\d{15}$")) -> "✅ IMEI válido"
                                    deviceId.length == 15 -> "Solo números permitidos"
                                    else -> "${deviceId.length}/15 dígitos"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    deviceId.isEmpty() -> colorAzulOscuro.copy(alpha = 0.6f)
                                    deviceId.length == 15 && deviceId.matches(Regex("^\\d{15}$")) -> Color(
                                        0xFF4CAF50
                                    )

                                    deviceId.length == 15 -> colorRojo
                                    else -> Color(0xFFFFA500)
                                }
                            )
                            if (deviceId.isNotEmpty()) {
                                Text(
                                    text = "${deviceId.length}/15",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (deviceId.length == 15) Color(0xFF4CAF50) else colorRojo
                                )
                            }
                        }
                    }
                )

                if (isServiceRunning) {
                    InfoBanner(
                        icon = Icons.Default.Info,
                        message = "El IMEI no se puede modificar mientras el monitoreo está activo",
                        color = Color(0xFFFFA500)
                    )
                }
            }


            // ========== SECCIÓN: PERMISOS ==========
            ConfigCard(
                icon = Icons.Default.Lock,
                title = "Permisos",
                subtitle = "Permisos requeridos",
                statusBadge = {
                    if (permissionsStatus) {
                        StatusBadge(text = "Todos concedidos", color = Color(0xFF4CAF50))
                    } else null
                }
            ) {
                PermissionItemEnhanced(
                    label = "Ubicación precisa",
                    granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED,
                    description = "Permite obtener la ubicación exacta del dispositivo"
                )

                PermissionItemEnhanced(
                    label = "Ubicación en segundo plano",
                    granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true,
                    description = "Permite obtener ubicación cuando la app está en segundo plano"
                )

                PermissionItemEnhanced(
                    label = "Notificaciones",
                    granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true,
                    description = "Permite mostrar notificaciones del servicio"
                )

                PermissionItemEnhanced(
                    label = "Servicio en primer plano",
                    granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.FOREGROUND_SERVICE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true,
                    description = "Permite ejecutar el servicio en primer plano"
                )

                if (!permissionsStatus) {
                    Button(
                        onClick = {
                            val activity = context as? MainActivity
                            activity?.checkAndRequestPermissions()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorAzulOscuro
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Solicitar permisos", color = Color.White)
                    }
                }
            }

            // ========== SECCIÓN: BATERÍA ==========
            ConfigCard(
                icon = Icons.Default.Battery5Bar,
                title = "Batería",
                subtitle = "Optimización de energía",
                statusBadge = {
                    if (isBatteryOptimizationOff) {
                        StatusBadge(text = "Optimizado", color = Color(0xFF4CAF50))
                    } else {
                        StatusBadge(text = "Pendiente", color = Color(0xFFFFA500))
                    }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isBatteryOptimizationOff)
                                "Optimización desactivada"
                            else
                                "Optimización activa",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF1A1A2E)
                        )
                        Text(
                            text = if (isBatteryOptimizationOff)
                                "El GPS funcionará correctamente"
                            else
                                "Recomienda desactivar para mejor rendimiento",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isBatteryOptimizationOff) Color(0xFF4CAF50) else Color(
                                0xFF757575
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val intent =
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBatteryOptimizationOff)
                                colorAzulMedio
                            else
                                colorRojo
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (isBatteryOptimizationOff) "Configurado" else "Ajustar",
                            color = Color.White
                        )
                    }
                }
            }

            // ========== SECCIÓN: INFORMACIÓN DEL SISTEMA ==========
            ConfigCard(
                icon = Icons.Default.Info,
                title = "Información del Sistema",
                subtitle = "Detalles del dispositivo"
            ) {
                InfoItemEnhanced(
                    icon = Icons.Default.Info,
                    label = "Versión de la App",
                    value = "1.0.0",
                    colorAzulOscuro = colorAzulOscuro
                )

                InfoItemEnhanced(
                    icon = Icons.Default.Android,
                    label = "Android SDK",
                    value = Build.VERSION.SDK_INT.toString(),
                    colorAzulOscuro = colorAzulOscuro
                )

                InfoItemEnhanced(
                    icon = Icons.Default.PhoneIphone,
                    label = "Modelo",
                    value = Build.MODEL,
                    colorAzulOscuro = colorAzulOscuro
                )

                InfoItemEnhanced(
                    icon = Icons.Default.Info,
                    label = "Fabricante",
                    value = Build.MANUFACTURER,
                    colorAzulOscuro = colorAzulOscuro
                )
            }

            // Espacio al final
            Spacer(modifier = Modifier.height(16.dp))

            ConfigCard(
                icon = Icons.Default.Build,
                title = "Diagnóstico",
                subtitle = "Verificar conexión con el servidor"
            ) {
                var diagnosticResult by remember { mutableStateOf("") }
                var isRunning by remember { mutableStateOf(false) }
                // ✅ Usamos un scope local para garantizar que funcione
                val localScope = rememberCoroutineScope()

                Button(
                    onClick = {
                        isRunning = true
                        diagnosticResult = "⏳ Ejecutando pruebas...\n"
                        // Ejecutar en corrutina con el scope local
                        localScope.launch {
                            try {
                                val result = runDiagnostics(context)
                                diagnosticResult = result
                            } catch (e: Exception) {
                                diagnosticResult = "❌ Error inesperado: ${e.message}\n${e.stackTraceToString()}"
                            } finally {
                                isRunning = false
                            }
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorAzulOscuro
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (isRunning) "⏳ Probando..." else "▶️ Ejecutar diagnóstico",
                        color = Color.White
                    )
                }

                if (diagnosticResult.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = diagnosticResult,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== COMPONENTES REUTILIZABLES ====================
suspend fun runDiagnostics(context: Context): String {
    return try {
        val result = StringBuilder()
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // 1. Verificar URL base
        val config = ConfigManager.getInstance(context)
        val baseUrl = config.getApiBaseUrl()
        result.append("📡 URL Base: $baseUrl\n")

        // 2. Verificar conexión a Internet
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = networkCapabilities != null &&
                (networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
        result.append("📶 Conexión a Internet: ${if (isConnected) "✅ SÍ" else "❌ NO"}\n")

        if (!isConnected) {
            result.append("❌ Sin conexión a Internet. Verifica Wi-Fi/Datos.\n")
            return result.toString()
        }

        // 3. Resolver DNS
        try {
            val host = URL(baseUrl).host
            val inetAddress = java.net.InetAddress.getByName(host)
            result.append("🌐 Resolución DNS: ${inetAddress.hostAddress} (${host})\n")
        } catch (e: Exception) {
            result.append("❌ Error DNS: ${e.message}\n")
            return result.toString()
        }

        // 4. Probar conexión HTTP a la API
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/tp/comercial/public/monitoreo/location")
                .head()
                .build()
            val response = client.newCall(request).execute()
            result.append("🔄 Prueba de conexión: Código ${response.code}\n")
            if (response.isSuccessful) {
                result.append("✅ Servidor responde correctamente.\n")
            } else {
                result.append("⚠️ Respuesta inesperada: ${response.message}\n")
            }
            response.close()
        } catch (e: Exception) {
            result.append("❌ Error de conexión: ${e.message}\n")
            return result.toString()
        }

        // 5. Verificar IMEI configurado
        val deviceId = prefs.getString("device_id", "")
        result.append("📱 IMEI configurado: ${if (deviceId.isNullOrEmpty()) "❌ NO" else "✅ $deviceId"}\n")
        if (deviceId.isNullOrEmpty() || deviceId.length != 15) {
            result.append("⚠️ IMEI no configurado o inválido.\n")
        }

        result.append("\n✅ Diagnóstico completado.")
        result.toString()
    } catch (e: Exception) {
        "❌ Error en diagnóstico: ${e.message}\n${e.stackTraceToString()}"
    }
}
@Composable
fun ConfigCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusBadge: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = Color(0xFF001A7F).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color(0xFF001A7F),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A2E)
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF757575)
                        )
                    }
                }

                statusBadge?.invoke()
            }

            Divider(
                color = Color(0xFFEEEEEE),
                thickness = 0.5.dp
            )

            content()
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PermissionItemEnhanced(
    label: String,
    granted: Boolean,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Dangerous,
            contentDescription = if (granted) "Permitido" else "Sin Permiso",
            tint = if (granted) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(20.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) Color(0xFF1A1A2E) else Color(0xFFF44336)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF757575),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            color = if (granted) Color(0xFF4CAF50).copy(alpha = 0.12f) else Color(0xFFF44336).copy(
                alpha = 0.12f
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (granted) "Concedido" else "Denegado",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun InfoItemEnhanced(
    icon: ImageVector,
    label: String,
    value: String,
    colorAzulOscuro: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorAzulOscuro.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF424242)
            )
        }

        Surface(
            color = Color(0xFFF5F5F5),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colorAzulOscuro,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}



@Composable
fun InfoBanner(
    icon: ImageVector,
    message: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
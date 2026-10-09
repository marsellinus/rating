package com.ratig.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ratig.app.data.offline.RememberLoginStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** One onboarding page: a large icon, a short title, and simple steps. */
private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val intro: String,
    val steps: List<String>,
)

private val PAGES = listOf(
    OnboardingPage(
        icon = Icons.Rounded.TouchApp,
        title = "Selamat Datang di RATIG",
        intro = "Aplikasi ini membantu memeriksa kelelahan pekerja dengan cepat " +
            "dan mudah. Hanya butuh beberapa menit.",
        steps = listOf(
            "Anda cukup mengikuti petunjuk di layar.",
            "Tidak perlu keahlian khusus — ikuti saja langkahnya.",
            "Gambar dan tulisan dibuat besar agar mudah dibaca.",
        ),
    ),
    OnboardingPage(
        icon = Icons.Rounded.Badge,
        title = "1. Pilih Pekerja",
        intro = "Tentukan siapa yang akan diperiksa.",
        steps = listOf(
            "Buka menu \"Pekerja\" di bagian bawah.",
            "Cari nama pekerja, atau pindai kartu (NIK/QR).",
            "Ketuk nama pekerja untuk membuka datanya.",
        ),
    ),
    OnboardingPage(
        icon = Icons.Rounded.Fingerprint,
        title = "2. Jalankan Pemeriksaan",
        intro = "Pekerja mengerjakan tes waktu reaksi singkat.",
        steps = listOf(
            "Ketuk tombol \"Mulai Tes\".",
            "Bacakan petunjuk singkat di layar.",
            "Pekerja menyentuh layar saat muncul tanda.",
            "Selesaikan sampai semua percobaan habis.",
        ),
    ),
    OnboardingPage(
        icon = Icons.Rounded.CheckCircle,
        title = "3. Lihat Hasil",
        intro = "Hasil muncul langsung setelah tes selesai.",
        steps = listOf(
            "Baca ringkasan: rata-rata waktu reaksi dan kategori.",
            "Ketuk \"Finalisasi\" untuk menyimpan hasil.",
            "Hasil yang sudah disimpan tidak dapat diubah.",
        ),
    ),
    OnboardingPage(
        icon = Icons.Rounded.CloudDone,
        title = "4. Aman Tanpa Internet",
        intro = "Aplikasi tetap berfungsi walaupun tidak ada sinyal.",
        steps = listOf(
            "Hasil disimpan di perangkat lebih dulu.",
            "Saat ada internet, data terkirim otomatis.",
            "Buka \"Status Sinkronisasi\" untuk melihat statusnya.",
        ),
    ),
)

/**
 * First-run tutorial. Shown once (the caller records that it was seen) and
 * always reachable later from the profile menu as "Panduan Penggunaan".
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val rememberLoginStore: RememberLoginStore,
) : ViewModel() {
    /** Records that the tutorial was completed or skipped, then navigates on. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            rememberLoginStore.setOnboardingDone()
            onDone()
        }
    }
}

@Composable
fun OnboardingRoute(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == PAGES.lastIndex
    val finish = { viewModel.finish(onDone) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = finish) { Text("Lewati") }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                OnboardingPageContent(PAGES[page])
            }

            // Page indicator dots.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(PAGES.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 5.dp)
                            .size(if (selected) 12.dp else 9.dp)
                            .background(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }

            Button(
                onClick = {
                    if (isLast) {
                        finish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = if (isLast) "Mulai Menggunakan" else "Lanjut",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(132.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = page.intro,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            page.steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

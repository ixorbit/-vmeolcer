package com.example.myapplication

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DataListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noFilesView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_list)

        // Toolbar'ı ayarla
        supportActionBar?.apply {
            title = "Kayıtlı Sensör Verileri"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            elevation = 8f
        }

        // UI elemanlarını bağla
        recyclerView = findViewById(R.id.recyclerView)
        noFilesView = findViewById(R.id.noFilesView)

        // RecyclerView'ı ayarla
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        // Dosyaları al ve filtrele
        val filesDir = getExternalFilesDir(null)

        val fileList = filesDir?.listFiles()?.filter {
            it.isFile && it.name.endsWith(".txt")
        }?.sortedByDescending {
            it.lastModified()  // En son kaydedilenleri üstte göster
        }?.map { it.name } ?: emptyList()

        // Eğer dosya yoksa, boş durumu göster
        if (fileList.isEmpty()) {
            showEmptyState()
            return
        }

        // Düzen yöneticisini ayarla
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        // Öğe ayırıcı ekle
        val dividerItemDecoration = DividerItemDecoration(
            recyclerView.context,
            layoutManager.orientation
        )
        recyclerView.addItemDecoration(dividerItemDecoration)

        // Adaptörü ayarla
        val adapter = FileListAdapter(fileList, this)
        recyclerView.adapter = adapter

        // Animasyon ekle
        recyclerView.alpha = 0f
        recyclerView.visibility = View.VISIBLE
        recyclerView.animate().alpha(1f).duration = 500

        // Boş durumu gizle
        noFilesView.visibility = View.GONE
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE

        noFilesView.visibility = View.VISIBLE
        val noFilesText = noFilesView.findViewById<TextView>(R.id.noFilesTextView)
        noFilesText.text = "Henüz kaydedilmiş veri bulunmuyor.\nVeri kaydetmek için ana ekrana dönün."

        // Uyarı kartı için animasyon
        val cardView = noFilesView.findViewById<CardView>(R.id.emptyStateCard)
        cardView.alpha = 0f
        cardView.translationY = 50f
        cardView.visibility = View.VISIBLE

        // Yavaşça görünür hale getir ve yukarı hareket ettir
        cardView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .start()

        // Dikkat çekmek için hafif bir pulse animasyonu
        ObjectAnimator.ofFloat(cardView, "scaleX", 0.95f, 1.05f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        ObjectAnimator.ofFloat(cardView, "scaleY", 0.95f, 1.05f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    // Geri düğmesi için destek
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // Özel animasyonlu geri dönüş
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
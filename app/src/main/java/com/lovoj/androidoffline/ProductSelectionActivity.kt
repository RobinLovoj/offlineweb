package com.lovoj.androidoffline

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import com.lovoj.androidoffline.Offlinewebview.OfflineWebview
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button

// Product data class
data class Product(
    val name: String,
    val imageRes: Int,
    val category: String, // "Men" or "Women"
    val price: String = ""
)

class ProductSelectionActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnMen: Button
    private lateinit var btnWomen: Button
    private lateinit var adapter: ProductAdapter
    private lateinit var allProducts: List<Product>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_selection)

        recyclerView = findViewById(R.id.recyclerProducts)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        btnMen = findViewById(R.id.btnMen)
        btnWomen = findViewById(R.id.btnWomen)

        allProducts = listOf(
            Product("Shirt", R.drawable.men_shirt, "Men", "$100.0"),
            Product("Suit", R.drawable.suit, "Men", "$200.0"),
            Product("Bandhgala Suit", R.drawable.bandhgala_suit, "Men", "$250.0"),
            Product("Half Jacket", R.drawable.half_jacket, "Men", "$120.0"),
            Product("Trench Coat", R.drawable.overcoat, "Men", "$180.0"),
            Product("Abayas", R.drawable.men_abayas, "Men", "$150.0"),
            Product("Kurta", R.drawable.kurta, "Men", "$90.0"),
            Product("Women Abayas", R.drawable.women_abayas, "Women", "$160.0"),
            Product("Ball Gown", R.drawable.ball_gown, "Women", "$300.0"),
            Product("Peplum Dress", R.drawable.peplum_dress, "Women", "$220.0"),
            Product("Bride Dress", R.drawable.bride_dress, "Women", "$400.0"),
            Product("Women Skirt", R.drawable.women_skirt, "Women", "$110.0")
        )

        adapter = ProductAdapter(emptyList()) { product ->
            openFabricForm(product.name)
        }
        recyclerView.adapter = adapter

        btnMen.setOnClickListener {
            setActiveTab("Men")
        }
        btnWomen.setOnClickListener {
            setActiveTab("Women")
        }
        setActiveTab("Men") // Default
    }

    private fun setActiveTab(category: String) {
        val filtered = allProducts.filter { it.category == category }
        adapter.updateProducts(filtered)
    }

    private fun openFabricForm(productName: String) {
        val url = "http://localhost:8080/index.html#/fabric-form?fabric=" + productName.replace(" ", "%20")
        val intent = Intent(this, OfflineWebview::class.java)
        intent.putExtra("fabric_url", url)
        startActivity(intent)
    }
}

class ProductAdapter(
    private var products: List<Product>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {
    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        private val txtProductName: TextView = itemView.findViewById(R.id.txtProductName)


        fun bind(product: Product) {
            imgProduct.setImageResource(product.imageRes)
            txtProductName.text = product.name

            itemView.setOnClickListener { onProductClick(product) }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount() = products.size

    fun updateProducts(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }
} 
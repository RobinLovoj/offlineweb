package com.lovoj.androidoffline

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lovoj.androidoffline.Offlinewebview.OfflineWebview
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import androidx.core.content.ContextCompat
import android.content.SharedPreferences
import org.json.JSONArray
import com.lovoj.androidoffline.ApiUtils
import android.widget.Toast
import android.content.Context
import android.util.Log
import kotlin.math.log

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
    private lateinit var loader: View
    private lateinit var adapter: ProductAdapter
    private var allProducts: List<Product> = emptyList() // Initialize as empty

    private val PREFS_NAME = "product_prefs"
    private val KEY_PRODUCT_LIST = "makingProductList"

    // Add static lists at the top of the class
    private val menCategories = listOf(
        Product("Shirt", R.drawable.men_shirt, "Men"),
        Product("Bandhgala\nSuit", R.drawable.bandhgala_suit, "Men"),
        Product("Suits", R.drawable.suit, "Men"),
        Product("Half\nJacket", R.drawable.half_jacket, "Men"),
        Product("Trench\nCoat", R.drawable.overcoat, "Men"),
        Product("Abayas", R.drawable.men_abayas, "Men")
    )
    private val womenCategories = listOf(
        Product("Kurti", R.drawable.kurta, "Women"),
        Product("Trench\nCoat", R.drawable.overcoat, "Women"),
        Product("Suits", R.drawable.suit, "Women"),
        Product("Shirt", R.drawable.men_shirt, "Women"),
        Product("One Piece\nDress", R.drawable.one_piece, "Women"),
        Product("Abayas", R.drawable.women_abayas, "Women"),
        Product("Skirt", R.drawable.women_skirt, "Women")
    )
    private var filteredMenCategories: List<Product> = emptyList()
    private var filteredWomenCategories: List<Product> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_selection)

        // Initialize views
        recyclerView = findViewById(R.id.recyclerProducts)
        btnMen = findViewById(R.id.btnMen)
        btnWomen = findViewById(R.id.btnWomen)
        loader = findViewById(R.id.loader)
        loader.visibility = View.VISIBLE // Show loader immediately

        recyclerView.layoutManager = GridLayoutManager(this, 4)

        // All products
        // Removed static allProducts assignment. Product list is now loaded only via loadOrFetchProductList().

        adapter = ProductAdapter(emptyList()) { product ->
            openFabricForm(product.name)
        }
        recyclerView.adapter = adapter

        // Set click listeners
        btnMen.setOnClickListener { setActiveTab("Men") }
        btnWomen.setOnClickListener { setActiveTab("Women") }
        setActiveTab("Men")

        // Apply initial rounded style
         loadOrFetchProductList() // This is now called in loadOrFetchProductList()
    }

    private fun loadOrFetchProductList() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_PRODUCT_LIST, null)
        if (cached != null) {
            loader.visibility = View.GONE
            val list = JSONArray(cached)
            val apiProductNames = mutableListOf<String>()
            for (i in 0 until list.length()) {
                apiProductNames.add(list.getString(i).trim())
            }
            filteredMenCategories = menCategories.filter { apiProductNames.contains(it.name) }
            filteredWomenCategories = womenCategories.filter { apiProductNames.contains(it.name) }
            setActiveTab(currentTab)
        } else {
            loader.visibility = View.VISIBLE
            val token = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("token", null)
            if (token == null) {
                loader.visibility = View.GONE
                Toast.makeText(this, "No token found. Please login again.", Toast.LENGTH_LONG).show()
                android.util.Log.e("ProductSelection", "No token found in app_prefs!")
                return
            }
            android.util.Log.d("ProductSelection", "Calling API with token: $token")
            ApiUtils.fetchMakingProductList(this, token, { productList ->
                android.util.Log.d("ProductSelection", "API call successful. Products: ${productList.size}")
                prefs.edit().putString(KEY_PRODUCT_LIST, JSONArray(productList).toString()).apply()
                val apiProductNames = productList.map { it.trim() }
                filteredMenCategories = menCategories.filter { apiProductNames.contains(it.name) }
                filteredWomenCategories = womenCategories.filter { apiProductNames.contains(it.name) }
                runOnUiThread {
                    loader.visibility = View.GONE
                    setActiveTab(currentTab)
                }
            }, { error ->
                android.util.Log.e("ProductSelection", "API call failed: $error")
                runOnUiThread {
                    loader.visibility = View.GONE
                    Toast.makeText(this, "Failed to load products: $error", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private var currentTab: String = "Men"
    private fun setActiveTab(category: String) {
        currentTab = category
        val selectedColor = ContextCompat.getColor(this, R.color.purple_500)
        val unselectedColor = ContextCompat.getColor(this, R.color.white)
        val selectedTextColor = ContextCompat.getColor(this, R.color.white)
        val unselectedTextColor = ContextCompat.getColor(this, R.color.grey_text)

        applyRoundedButtonStyle(btnMen, unselectedColor, unselectedTextColor)
        applyRoundedButtonStyle(btnWomen, unselectedColor, unselectedTextColor)

        when (category) {
            "Men" -> applyRoundedButtonStyle(btnMen, selectedColor, selectedTextColor)
            "Women" -> applyRoundedButtonStyle(btnWomen, selectedColor, selectedTextColor)
        }

        // Show only filtered categories for the selected tab
        when (category) {
            "Men" -> adapter.updateProducts(filteredMenCategories)
            "Women" -> adapter.updateProducts(filteredWomenCategories)
        }
    }

    private fun getCurrentTabCategory(): String = currentTab

    private fun getProductCategory(productName: String): String {
        val name = productName.trim().lowercase()
        return when {
            name.contains("women") || name.contains("skirt") || name.contains("kurti") || name.contains("dupatta") -> "Women"
            else -> "Men"
        }
    }

    private fun getProductImageRes(productName: String): Int {
        return when (productName.trim().lowercase()) {
            "shirt" -> R.drawable.men_shirt
            "pant" -> R.drawable.cropped_pants
            "blazer" -> R.drawable.blazer
            "bandhgala suit" -> R.drawable.bandhgala_suit
            "suits" -> R.drawable.suit
            "half jacket" -> R.drawable.half_jacket
            "trench coat" -> R.drawable.overcoat
            "abayas" -> R.drawable.men_abayas
            "kurti" -> R.drawable.kurta
            "bottom wear" -> R.drawable.cropped_pants
            "dupatta" -> R.drawable.spl
            "women shirt" -> R.drawable.men_shirt
            "women pant" -> R.drawable.cropped_pants
            "women blazer" -> R.drawable.blazer
            "women suit" -> R.drawable.suit
            "one piece dress" -> R.drawable.one_piece
            "women trench coat" -> R.drawable.overcoat
            "women abayas" -> R.drawable.women_abayas
            "skirt" -> R.drawable.women_skirt
            else -> R.drawable.suit
        }
    }

    private fun applyRoundedButtonStyle(button: Button, bgColor: Int, textColor: Int) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(bgColor)
        }
        button.background = drawable
        button.setTextColor(textColor)
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
            txtProductName.visibility = View.VISIBLE
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
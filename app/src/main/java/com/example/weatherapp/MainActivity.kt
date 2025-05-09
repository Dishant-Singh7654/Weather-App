package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.Models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mSharedPreferences: SharedPreferences
    private var mProgressDialog: Dialog? = null
    private var tvMain : TextView? = null
    private var tvMainDescription: TextView? = null
    private var tvTemp: TextView? = null
    private var tvSunriseTime: TextView? = null
    private var tvSunsetTime: TextView? = null
    private var tvHumidity: TextView? = null
    private var tvMin: TextView? = null
    private var tvMax: TextView? = null
    private var tvSpeed: TextView? = null
    private var tvName: TextView? = null
    private var tvCountry: TextView? = null
    private var ivMain: ImageView? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        setupUI()
        tvMain = findViewById(R.id.tv_main)
        tvMainDescription = findViewById(R.id.tv_main_description)
        tvTemp = findViewById(R.id.tv_temp)
        tvSunriseTime = findViewById(R.id.tv_sunrise_time)
        tvSunsetTime = findViewById(R.id.tv_sunset_time)
        tvHumidity = findViewById(R.id.tv_humidity)
        tvMin = findViewById(R.id.tv_min)
        tvMax = findViewById(R.id.tv_max)
        tvSpeed = findViewById(R.id.tv_speed)
        tvName = findViewById(R.id.tv_name)
        tvCountry = findViewById(R.id.tv_country)
        ivMain = findViewById(R.id.iv_main)
        if(!isLocationEnabled()){
            Toast.makeText(this,
                "Location Provider is turned off. Please turn on in settings",
                Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        requestLocationData()
                    }
                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@MainActivity,
                            "Please turn on the Location", Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }
            }).onSameThread().check()
        }
    }
    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback, Looper.myLooper()
        )
    }
    private val mLocationCallback = object :LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation : Location = locationResult.lastLocation!!
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")
            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }
    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit: Retrofit = Retrofit.Builder().
            baseUrl(Constants.BASE_URL).
            addConverterFactory(GsonConverterFactory.create()).build()
            val service: WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )
            showCustomProgressDialog()
            listCall.enqueue(object :Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){
                        hideProgressDialog()
                        val weatherList: WeatherResponse = response.body()!!
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                        Log.i("Response Result:", "$weatherList")
                    }else{
                        val rc = response.code()
                        when(rc){
                            400->{
                                Log.i("Error 400","Bad Connection")
                            }
                            404->{
                                Log.i("Error 404","Not Found")
                            }
                            else ->{
                                Log.i("Error","Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                   Log.i("Errorrrr!", t.message.toString())
                    hideProgressDialog()
                }

            })
        }else{
            Toast.makeText(this,
                "You are not connected with internet",
                Toast.LENGTH_LONG).show()
        }
    }
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this).setMessage(
            "You have turned off the Location. Please turn on it."
        ).setPositiveButton("Go To Settings") {_,_->
            try{
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }catch (e: ActivityNotFoundException){
                e.printStackTrace()
            }

        }.setNegativeButton("Cancel"){dialog,_->
            dialog.dismiss()
        }.show()
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }
    private fun hideProgressDialog(){
        if (mProgressDialog!=null){
            mProgressDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    @SuppressLint("SetTextI18n")
    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if(!weatherResponseJsonString.isNullOrBlank()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for(i in weatherList.weather.indices){
                tvMain?.text = weatherList.weather[i].main
                tvMainDescription?.text = weatherList.weather[i].description
                tvTemp?.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                tvHumidity?.text = weatherList.main.humidity.toString() + "%"
                tvMin?.text =weatherList.main.temp_min.toString() + getUnit(application.resources.configuration.locales.toString())
                tvMax?.text = weatherList.main.temp_max.toString() + getUnit(application.resources.configuration.locales.toString())
                tvSpeed?.text = weatherList.wind.speed.toString()
                tvName?.text = weatherList.name
                tvCountry?.text =weatherList.sys.country
                tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
                tvSunsetTime?.text = unixTime(weatherList.sys.sunset)

                when(weatherList.weather[i].icon){
                    "01d" -> ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> ivMain?.setImageResource(R.drawable.few_clouds)
                    "03d" -> ivMain?.setImageResource(R.drawable.scattered_clouds)
                    "04d" -> ivMain?.setImageResource(R.drawable.broken_clouds)
                    "09d" -> ivMain?.setImageResource(R.drawable.shower_rain)
                    "10d" -> ivMain?.setImageResource(R.drawable.rainy)
                    "11d" -> ivMain?.setImageResource(R.drawable.thunderstorm)
                    "13d" -> ivMain?.setImageResource(R.drawable.snowflake)
                    "50d" -> ivMain?.setImageResource(R.drawable.mist)
                    "01n" -> ivMain?.setImageResource(R.drawable.moon)
                    "02n" -> ivMain?.setImageResource(R.drawable.few_clouds_moon)
                    "03n" -> ivMain?.setImageResource(R.drawable.scattered_clouds)
                    "04n" -> ivMain?.setImageResource(R.drawable.broken_clouds)
                    "09n" -> ivMain?.setImageResource(R.drawable.shower_rain)
                    "10n" -> ivMain?.setImageResource(R.drawable.rainy)
                    "11n" -> ivMain?.setImageResource(R.drawable.thunderstorm)
                    "13n" -> ivMain?.setImageResource(R.drawable.snowflake)
                    "50n" -> ivMain?.setImageResource(R.drawable.mist)
                }
            }
        }

    }
    private fun getUnit(value: String): String{
        var valueTemp = "°C"
        if ("US" == value || "LR" == value || "MM" == value){
            valueTemp = "°F"
        }
        return valueTemp
    }
    @SuppressLint("SimpleDateFormat")
    private fun unixTime(timex: Long): String?{
        val date = Date(timex *1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}
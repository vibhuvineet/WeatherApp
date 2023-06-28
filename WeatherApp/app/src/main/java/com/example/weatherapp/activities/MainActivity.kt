package com.example.weatherapp.activities

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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import com.example.weatherapp.R
import com.example.weatherapp.utils.Constants
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var binding: ActivityMainBinding

    private var mProgressDialog: Dialog? = null

    private var mLatitude: Double =0.0
    private var mLongitude: Double =0.0

    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        mFusedLocationClient=LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

        setupUI()

        if(!isLocationEnabled()){
            Toast.makeText(this, "Your Location provider is turned off.Please turn it on", Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()){
                            requestLocationData()
                    }
                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity, "You have denied location permission.Please enable them as it is mandatory for the app to work",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                   override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                   ){
                    showRationalDialogForPermissions()
                }
            }).onSameThread()
                .check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest= LocationRequest()
        mLocationRequest.priority=LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation!!
            mLatitude = mLastLocation.latitude
            Log.i("Current Latitude","$mLatitude")

            mLongitude= mLastLocation.longitude
            Log.i("Current Longitude","$mLongitude")

            getLocationWeatherDetails()
        }
    }

   private fun getLocationWeatherDetails(){
        if (Constants.isNetworkAvailable(this)){
            val retrofit: Retrofit=Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService = retrofit
                .create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                mLatitude,mLongitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object: Callback<WeatherResponse>{

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrr",t.message.toString())
                    hideProgressDialog()
                }

                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){

                        hideProgressDialog()
                        val weatherList: WeatherResponse = response.body()!!

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor=mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                        Log.i("Response Result","$weatherList")
                    }else{
                        val rc=response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400","Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404","Not Found")
                            }
                            else ->{
                                Log.e("Error","Generic Error")
                            }
                        }
                    }
                }


            })

        }else{
            Toast.makeText(this, "No internet connection available.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this).setMessage("It looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "Go To SETTINGS"
            ) { _,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri= Uri.fromParts("package",packageName,null)
                    intent.data=uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                _ ->
                dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled() : Boolean {
        val locationManager: LocationManager=
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled( LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }
    private fun hideProgressDialog(){
        if(mProgressDialog!=null){
          mProgressDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                getLocationWeatherDetails()
                true
            }else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList= Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            for(i in weatherList.weather.indices){
                Log.i("Weather Name",weatherList.weather.toString())

                binding.tvMain.text = weatherList.weather[i].main
                binding.tvMainDescription.text = weatherList.weather[i].description
                binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.toString())
                binding.tvHumidity.text=weatherList.main.humidity.toString() + " per cent"

                binding.tvSunriseTime.text=unixTime(weatherList.sys.sunrise)
                binding.tvSunsetTime.text=unixTime(weatherList.sys.sunset)

                binding.tvMin.text=weatherList.main.temp_min.roundToInt().toString() + "° min"
                binding.tvMax.text=weatherList.main.temp_max.roundToInt().toString() + "° max"

                binding.tvSpeed.text=weatherList.wind.speed.toString()
                binding.tvName.text=weatherList.name
                binding.tvCountry.text=weatherList.sys.country

                when(weatherList.weather[i].icon){
                    "01d" ->  binding.ivMain.setImageResource(R.drawable.sunny)
                    "02d" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "03d" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "04d" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "04n" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "10d" ->  binding.ivMain.setImageResource(R.drawable.rain)
                    "11d" ->  binding.ivMain.setImageResource(R.drawable.storm)
                    "13d" ->  binding.ivMain.setImageResource(R.drawable.snowflake)
                    "01n" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "10n" ->  binding.ivMain.setImageResource(R.drawable.cloud)
                    "11n" ->  binding.ivMain.setImageResource(R.drawable.rain)
                    "13n" ->  binding.ivMain.setImageResource(R.drawable.snowflake)
                }
         }
        }
    }

    private fun getUnit(value: String):String?{
        var value = "°C"
        if("US " == value || "LR" == value || "MM" == value){
            value="°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String?{
        val date = Date(timex * 1000L)
        val sdf= SimpleDateFormat("HH:mm",Locale.UK)
        sdf.timeZone= TimeZone.getDefault()
        return sdf.format(date)
    }
}
package com.skye.skyenav

import android.Manifest
import android.graphics.Color
import com.google.android.gms.maps.model.PolylineOptions
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import android.location.Location
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.color.utilities.TonalPalette
import com.skye.skyenav.databinding.FragmentMapBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException


class MapFragment: Fragment(R.layout.fragment_map), OnMapReadyCallback{
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var mMap: GoogleMap
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var locationCallback: LocationCallback
    private var hasCenteredOnce = false
    private lateinit var locationRequest: LocationRequest
    private var currentPolyline: com.google.android.gms.maps.model.Polyline? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //inflates view with viewbinding
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Load and initialize map fragment
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
        fusedLocationProviderClient =LocationServices.getFusedLocationProviderClient(requireActivity())
        val originInput = view.findViewById<EditText>(R.id.originInput)
        val destinationInput = view.findViewById<EditText>(R.id.destinationInput)
        val stopButton = view.findViewById<Button>(R.id.stop_navigation_button)
        val searchButton = view.findViewById<Button>(R.id.search_button)
        val goButton = view.findViewById<Button>(R.id.start_navigation_button)
        searchButton.setOnClickListener{
            val origin = originInput.text.toString()
            val destination = destinationInput.text.toString()
            if(origin.isNotEmpty() && destination.isNotEmpty())
            {
                geocodeAndDrawRoute(origin, destination)
            }
            else
            {
                Toast.makeText(requireContext(), "Please Enter both addresses", Toast.LENGTH_SHORT).show();
            }
        }

        goButton.setOnClickListener{
            val destination = view.findViewById<EditText>(R.id.destinationInput).text.toString()
            val geocoder = Geocoder(requireContext())
            val destinationList = geocoder.getFromLocationName(destination, 1)

            if(destinationList.isNullOrEmpty())
            {
                Toast.makeText(requireContext(), "Invalid destination", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val destinationLatLng = LatLng(destinationList[0].latitude, destinationList[0].longitude)

            createLocationRequest()
            setupLocationCallback(destinationLatLng)
            // Start real-time location updates
            if(ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            {
                fusedLocationProviderClient.requestLocationUpdates(locationRequest,  locationCallback, null)
            }

            binding.searchLayout.visibility = View.GONE
            goButton.visibility = View.GONE
            stopButton.visibility = View.VISIBLE
            //hide non-essential buttons during navigation.
        }
        stopButton.setOnClickListener{
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
            Toast.makeText(requireContext(), "Navigation Stopped", Toast.LENGTH_SHORT).show()
            stopButton.visibility = View.GONE
        }
    }
    override fun onMapReady(googleMap: GoogleMap)
    {
        //map is ready, enable user location if permissible.
        mMap = googleMap
        if(ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }
    }

    private fun geocodeAndDrawRoute(origin: String, destination: String)
    {
        //convert addresses to coordinates + drawing markers
        val geocoder = Geocoder(requireContext())
        val originList = geocoder.getFromLocationName(origin, 1)
        val destinationList = geocoder.getFromLocationName(destination,1)
        if(originList.isNullOrEmpty() || destinationList.isNullOrEmpty())
        {
            Toast.makeText(requireContext(), "Unable to geocode addresses", Toast.LENGTH_SHORT).show()
            return
        }
        val originLatLng = LatLng(originList[0].latitude, originList[0].longitude)
        val destinationLatLng = LatLng(destinationList[0].latitude, destinationList[0].longitude)

        mMap.clear()
        mMap.addMarker(MarkerOptions().position(originLatLng).title("Origin"))
        mMap.addMarker(MarkerOptions().position(destinationLatLng).title("Destination"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(originLatLng, 12f))

        fetchRoute(originLatLng, destinationLatLng)


    }
    fun getDirectionsUrl(origin: LatLng, dest: LatLng): String
    {
        //Build google Directions API URL using origin and destination
        val strOrigin = "origin=${origin.latitude},${origin.longitude}"
        val strDest = "destination=${dest.latitude},${dest.longitude}"
        val key = "YOUR MAPS API KEY HERE"
        return "https://maps.googleapis.com/maps/api/directions/json?$strOrigin&$strDest&key=$key"
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == LOCATION_PERMISSION_REQUEST_CODE){
            if((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)){
                mMap.isMyLocationEnabled = true
            }
            else{
                Toast.makeText(requireContext(),"Location Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }//enable location if granted
    }

    fun parseStepsFromJson(jsonData: String): List<LatLng>
    {
        // Parse data encoded route from JSON
        val result = mutableListOf<LatLng>()
        val json = JSONObject(jsonData)
        val routes = json.getJSONArray("routes")
        if(routes.length() == 0) return result
        val points = routes.getJSONObject(0)
            .getJSONObject("overview_polyline")
            .getString("points")
        return decodePolyline(points)
    }

    fun decodePolyline(encoded: String):List<LatLng>
    {
        //Decoding Google's polyline info into latitude and longitude coordinates.
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while(index < len)
        {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5

            } while (b >= 0x20)
            val dlat = if (result and 1 != 0 ) result.inv() shr 1 else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) result.inv() shr 1 else result shr 1
            lng += dlng

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }

    fun drawPolyline(route: List<LatLng>)
    {
        val polylineOptions = PolylineOptions().addAll(route).color(Color.BLUE).width(10f)
        mMap.addPolyline(polylineOptions)
    }//drawing polyline between two location addresses



    fun fetchRoute(origin: LatLng, destination: LatLng)
    {
        val url = getDirectionsUrl(origin, destination)
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        // Request route from Google Directions API
        client.newCall(request).enqueue(object :Callback
        {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body()?.string()
                val steps = parseStepsFromJson(body ?: "")
                activity?.runOnUiThread{
                    drawPolyline(steps)
                    val startNavButton = view?.findViewById<Button>(R.id.start_navigation_button)
                    startNavButton?.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun setupLocationCallback(destinationLatLng: LatLng)
    {
        locationCallback = object : LocationCallback()
        {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.lastLocation ?: return
                val userLatLng = LatLng(location.latitude, location.longitude)

                mMap.clear()
                mMap.addMarker(MarkerOptions().position(userLatLng).title("You"))
                mMap.addMarker(MarkerOptions().position(destinationLatLng).title("Destination"))
                if(!hasCenteredOnce)
                {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng,15f))
                    hasCenteredOnce = true
                }
                //show location once if it hasn't refreshed location
                fetchRoute(userLatLng, destinationLatLng)
            }
        }
    }

    private fun createLocationRequest()
    {
        //create the request to get location in emulator
        locationRequest = LocationRequest.create().apply {

            interval = 5000
            fastestInterval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }



    override fun onDestroyView(){
        super.onDestroyView()
        _binding = null

    }

}
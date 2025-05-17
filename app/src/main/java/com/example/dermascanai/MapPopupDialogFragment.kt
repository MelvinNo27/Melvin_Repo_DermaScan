package com.example.dermascanai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import com.example.dermascanai.databinding.FragmentMapPopupDialogBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*

class MapPopupDialogFragment : DialogFragment(), OnMapReadyCallback {

    private var _binding: FragmentMapPopupDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var googleMap: GoogleMap
    private val dermaClinics = listOf(
        LatLng(7.4473, 125.8076), // Tagum Global Medical Center
        LatLng(7.4481, 125.8057), // Dr. Sherill Ann D. Mendoza (St. Therese Maternity Clinic)
        LatLng(7.4456, 125.8070), // Skin Republik
        LatLng(7.4470, 125.8072), // Derma C Skin and Laser Center
        LatLng(7.4465, 125.8045)  // Bunagan Skin Clinic
    )


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapPopupDialogBinding.inflate(inflater, container, false)

        binding.closeButton.setOnClickListener {
            dismiss()
        }
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            1001
        )
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val mapFragment = childFragmentManager.findFragmentById(R.id.popupMapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true

        // Add markers for clinics
        for (location in dermaClinics) {
            googleMap.addMarker(MarkerOptions().position(location).title("Nearby Derma Clinic"))
        }

        // Move camera to user's location
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Handle missing permission
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLatLng = LatLng(location.latitude, location.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 12f))
                googleMap.addMarker(MarkerOptions().position(userLatLng).title("You are here"))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

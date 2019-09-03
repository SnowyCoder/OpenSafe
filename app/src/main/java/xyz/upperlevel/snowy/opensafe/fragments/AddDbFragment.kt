package xyz.upperlevel.snowy.opensafe.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_add_db.*
import xyz.upperlevel.snowy.opensafe.R
import xyz.upperlevel.snowy.opensafe.db.DbRegistry
import xyz.upperlevel.snowy.opensafe.db.DbTypeRegistry


class AddDbFragment : Fragment() {
    private lateinit var dbreg: DbRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            dbreg = it.getParcelable("dbreg")!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_add_db, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dbTypes = DbTypeRegistry.dbTypes.keys.toMutableList()
        val defIntex = dbTypes.indexOf(DbTypeRegistry.DEFAULT_TYPE.name)

        dbtype.adapter = ArrayAdapter<String>(context!!, android.R.layout.simple_spinner_dropdown_item, dbTypes)
        dbtype.setSelection(defIntex)

        add.setOnClickListener {
            val type = DbTypeRegistry.getByName(dbtype.selectedItem as String)!!
            val db = dbreg.create(type, dbname.text.toString(), password.text.toString())

            val bundle = Bundle()
            bundle.putParcelable("db", db)
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.addDbFragment, true)
                .build()
            findNavController().navigate(R.id.folderFragment, bundle, navOptions)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(dbreg: DbRegistry) =
            AddDbFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("dbreg", dbreg)
                }
            }
    }
}

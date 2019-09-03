package xyz.upperlevel.snowy.opensafe.activities

import android.os.Bundle
import android.os.StrictMode
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.findNavController
import xyz.upperlevel.snowy.opensafe.R

import kotlinx.android.synthetic.main.activity_main.*
import xyz.upperlevel.snowy.opensafe.db.DbRegistry
import xyz.upperlevel.snowy.opensafe.db.Database
import xyz.upperlevel.snowy.opensafe.fragments.DbListFragment
import xyz.upperlevel.snowy.opensafe.fragments.FolderFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().build())

        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        /*fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }*/

        findNavController(R.id.nav_host_fragment).addOnDestinationChangedListener { controller, destination, arguments ->

        }


        /*val db: Database? = intent?.extras?.getParcelable("db")
        val dbreg: DbRegistry? = intent?.extras?.getParcelable("dbreg")

        val fragmentTransaction = supportFragmentManager.beginTransaction()
        when {
            db != null -> {
                val bundle = Bundle()
                bundle.putParcelable("db", db)
                fragmentTransaction.add(R.id.fragment, FolderFragment::class.java, bundle, null)
            }
            dbreg != null -> {
                val bundle = Bundle()
                bundle.putParcelable("dbreg", dbreg)
                fragmentTransaction.add(R.id.fragment, DbListFragment::class.java, bundle, null)

                Toast.makeText(applicationContext!!, "Opening account list", Toast.LENGTH_LONG).show()
            }
            else -> throw IllegalArgumentException("No args found")
        }

        fragmentTransaction.commit()*/
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()

        return true
    }

}

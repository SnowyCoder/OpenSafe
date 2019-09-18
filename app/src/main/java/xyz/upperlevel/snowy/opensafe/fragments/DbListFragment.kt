package xyz.upperlevel.snowy.opensafe.fragments

import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_db_list.*
import kotlinx.android.synthetic.main.fragment_db_list_item.view.*
import xyz.upperlevel.snowy.opensafe.R
import xyz.upperlevel.snowy.opensafe.db.Database
import xyz.upperlevel.snowy.opensafe.db.DbRegistry


class DbListFragment : Fragment() {
    lateinit var dbreg: DbRegistry


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbreg = arguments?.getParcelable("dbreg")!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_db_list, container, false)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set the adapter
        with(list) {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = MyItemRecyclerViewAdapter()
        }

        if (dbreg.dbs.isEmpty()) {
            list.visibility = View.GONE
            searchEmptyView.visibility = View.VISIBLE
        } else {
            list.visibility = View.VISIBLE
            searchEmptyView.visibility = View.GONE
        }

        addDb.setOnClickListener {
            val bundle = Bundle()
            bundle.putParcelable("dbreg", dbreg)
            findNavController().navigate(R.id.addDbFragment, bundle)
        }
    }

    override fun onContextItemSelected(mitem: MenuItem): Boolean {
        val item = dbreg.dbs[mitem.groupId]

        when (mitem.itemId) {
            MENU_ACTION_OPEN -> openDb(item)
            MENU_ACTION_DELETE -> deleteDb(mitem.groupId)
            else -> super.onContextItemSelected(mitem)
        }

        return true
    }

    fun openDb(db: Database) {
        val builder = AlertDialog.Builder(context!!)
        builder.setTitle("Password")

        // Set up the input
        val input = EditText(context!!)
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            if (db.tryUnlock(input.text.toString())) {
                val bundle = Bundle()
                bundle.putParcelable("db", db)
                findNavController().navigate(R.id.folderFragment, bundle)
            } else {
                Toast.makeText(context!!, "Wrong password", Toast.LENGTH_LONG).show()
            }
        }

        builder.show()
    }

    fun deleteDb(itemPos: Int) {
        val name = dbreg.dbs[itemPos].getName()
        AlertDialog.Builder(context!!)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle("Delete $name")
            .setMessage("Are you sure you want to delete $name? Any data will be lost")
            .setPositiveButton(android.R.string.yes) { _, _ -> dbreg.delete(itemPos)}
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    inner class MyItemRecyclerViewAdapter : RecyclerView.Adapter<ViewHolder>() {

        private val mOnClickListener: View.OnClickListener

        init {
            mOnClickListener = View.OnClickListener { v ->
                val item = v.tag as Database
                // Notify the active callbacks interface (the activity, if the fragment is attached to
                // one) that an item has been selected.
                Toast.makeText(context!!, "Clicked item: ${item.getName()}", Toast.LENGTH_LONG).show()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_db_list_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = dbreg.dbs[position]
            holder.contentView.text = item.getName()

            with(holder.mView) {
                tag = item
                setOnClickListener(mOnClickListener)
            }
        }

        override fun getItemCount(): Int = dbreg.dbs.size
    }

    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView),
        View.OnCreateContextMenuListener {
        val contentView: TextView = mView.content

        override fun toString(): String {
            return super.toString() + " '" + contentView.text + "'"
        }

        override fun onCreateContextMenu(
            menu: ContextMenu,
            v: View,
            menuInfo: ContextMenu.ContextMenuInfo?
        ) {
            menu.add(this.adapterPosition, MENU_ACTION_OPEN, ContextMenu.NONE, "Open")
            menu.add(this.adapterPosition, MENU_ACTION_DELETE, ContextMenu.NONE, "Delete")
        }
    }

    companion object {
        const val MENU_ACTION_OPEN = 0
        const val MENU_ACTION_DELETE = 1
    }
}

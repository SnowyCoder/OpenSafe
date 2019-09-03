package xyz.upperlevel.snowy.opensafe.fragments

import android.os.AsyncTask
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_calculator.*
import xyz.upperlevel.snowy.opensafe.Calculator
import xyz.upperlevel.snowy.opensafe.R
import xyz.upperlevel.snowy.opensafe.db.Database
import xyz.upperlevel.snowy.opensafe.db.DbRegistry
import java.io.File
import java.util.*


class CalculatorFragment : Fragment() {
    var equation: String = ""
    var error = false
    lateinit var dbreg: DbRegistry

    private var state: LoadState = LoadState.LOADING_DB
    private var passwordQueue: Queue<String> = ArrayDeque<String>()
    private var loadTask: LoadDbRegistryWorker? = null
    private var passwordTask: PasswordTrialWorker? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calculator, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity!!.title = "Calculator"

        val dbfolder = File(activity!!.applicationInfo.dataDir).resolve("dbs")
        dbfolder.mkdir()
        dbreg = DbRegistry(dbfolder)

        loadTask = LoadDbRegistryWorker(this, dbreg)
        loadTask!!.execute()

        setupCharButton(btn_plus, '+')
        setupCharButton(btn_minus, '-')
        setupCharButton(btn_multiply, '*')
        setupCharButton(btn_divide, '/')
        setupCharButton(btn_decimal, '.')
        setupCharButton(btn_power, '^')
        setupCharButton(btn_0, '0')
        setupCharButton(btn_1, '1')
        setupCharButton(btn_2, '2')
        setupCharButton(btn_3, '3')
        setupCharButton(btn_4, '4')
        setupCharButton(btn_5, '5')
        setupCharButton(btn_6, '6')
        setupCharButton(btn_7, '7')
        setupCharButton(btn_8, '8')
        setupCharButton(btn_9, '9')
        btn_root.setOnClickListener { wrapInRoot() }
        btn_clear.setOnClickListener { clear() }
        btn_equals.setOnClickListener {
            if (error) return@setOnClickListener
            val res = Calculator.calc(equation)
            equation = res?.toString() ?: "Err"
            error = res == null
            formula.text = equation
            result.text = ""
        }


        btn_percent.setOnClickListener { onPercentage() }
        btn_percent.setOnLongClickListener {
            val builder = AlertDialog.Builder(context!!)
            builder.setTitle("Equation")

            // Set up the input
            val input = EditText(context!!)
            // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            builder.setView(input)

            // Set up the buttons
            builder.setPositiveButton("OK") { dialog, which ->
                tryPassword(input.text.toString())
            }
            builder.setNeutralButton("Settings") { dialog, which -> openSettings() }

            builder.show()
            true
        }
    }

    fun onPercentage() {
        tryPassword(equation)
        addChar('%')
    }

    fun openSettings() {
        val bundle = Bundle()
        bundle.putParcelable("dbreg", dbreg)
        findNavController().navigate(R.id.dbListFragment, bundle)
    }

    fun setupCharButton(btn: Button, ch: Char) {
        btn.setOnClickListener { addChar(ch) }
    }

    fun clearIfError() {
        if (error) {
            error = false
            equation = ""
        }
    }

    fun addChar(ch: Char) {
        clearIfError()
        equation += ch
        update()
    }

    fun wrapInRoot() {
        clearIfError()
        equation = Calculator.SQRT + "(" + equation + ")"
        update()
    }

    fun clear() {
        equation = ""
        update()
    }

    fun update() {
        formula.text = equation
        result.text = Calculator.calc(equation)?.toString() ?: "Err"
    }

    fun onDbLoadComplete() {
        state = LoadState.READY
        loadTask = null
        if (!passwordQueue.isEmpty()) {
            startPasswordProcessor()
        }
    }

    fun startPasswordProcessor() {
        passwordTask = PasswordTrialWorker(this, dbreg)
        passwordTask!!.execute(passwordQueue.remove())
    }

    fun tryPassword(password: String) {
        passwordQueue.add(password)

        if (passwordTask == null && state == LoadState.READY) {
            // Password not executing
            startPasswordProcessor()
        }
    }

    fun onPasswordTrialResult(db: Database?) {
        if (db != null) {
            clear()

            val bundle = Bundle()
            bundle.putParcelable("db", db)
            findNavController().navigate(R.id.folderFragment, bundle)

            return
        } else {
            // Try next password
            if (!passwordQueue.isEmpty()) startPasswordProcessor()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        loadTask?.also {
            it.cancel(true)
            it.fragment = null
        }

        passwordTask?.also {
            it.cancel(true)
            it.fragment = null
        }
    }

    private class LoadDbRegistryWorker(var fragment: CalculatorFragment?, val dbreg: DbRegistry) : AsyncTask<Void, Void, Void?>() {
        override fun doInBackground(vararg params: Void?): Void? {
            dbreg.loadAll()
            return null
        }

        override fun onPostExecute(result: Void?) {
            if (isCancelled) return
            fragment?.onDbLoadComplete()
        }
    }

    private class PasswordTrialWorker(var fragment: CalculatorFragment?, val dbreg: DbRegistry) : AsyncTask<String, Void, List<Database>>() {
        override fun doInBackground(vararg params: String): List<Database> {
            return params.map { dbreg.tryUnlock(it) }.filterNotNull()
        }

        override fun onPostExecute(result: List<Database>?) {
            if (isCancelled) return
            fragment?.onPasswordTrialResult(result?.getOrNull(0))
        }
    }


    enum class LoadState {
        LOADING_DB, READY
    }
}

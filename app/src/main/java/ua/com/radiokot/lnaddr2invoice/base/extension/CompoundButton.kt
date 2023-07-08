package ua.com.radiokot.lnaddr2invoice.base.extension

import android.widget.Checkable
import android.widget.CompoundButton
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * Binds the given [liveData] to the [Checkable.isChecked] value in both directions.
 * The checked state is set to the [liveData], if differs,
 * as well as the state from the [liveData] is shown in the view.
 *
 * The view must be attached to a lifecycle owner.
 */
fun CompoundButton.bindTextTwoWay(
    liveData: MutableLiveData<Boolean>
) {
    val lifecycleOwner = findViewTreeLifecycleOwner()
        .checkNotNull {
            "The view must be attached to a lifecycle owner"
        }

    this.setOnCheckedChangeListener { _, isChecked ->
        if (liveData.value != isChecked) {
            liveData.value = isChecked
        }
    }

    liveData.observe(lifecycleOwner) { newIsChecked ->
        if (isChecked != newIsChecked) {
            isChecked = newIsChecked
        }
    }
}

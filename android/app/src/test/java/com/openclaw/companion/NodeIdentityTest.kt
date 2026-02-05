package com.openclaw.companion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NodeIdentityTest {

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("openclaw_prefs", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun getOrCreate_returnsSameValueAcrossCalls() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val first = NodeIdentity.getOrCreate(context)
    val second = NodeIdentity.getOrCreate(context)

    assertEquals(first, second)
    val stored = context.getSharedPreferences("openclaw_prefs", Context.MODE_PRIVATE)
      .getString("node_id", null)
    assertEquals(first, stored)
  }
}

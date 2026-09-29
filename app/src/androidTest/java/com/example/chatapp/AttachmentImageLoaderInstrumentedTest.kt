package com.example.chatapp

import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentImageLoaderInstrumentedTest {
    @Test
    fun clearInvalidatesRecycledImageViewRequest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageView = ImageView(context)

        AttachmentImageLoader.load(imageView, "https://example.test/image.jpg")
        AttachmentImageLoader.clear(imageView)

        assertNull(imageView.getTag(R.id.tag_attachment_image_url))
    }
}

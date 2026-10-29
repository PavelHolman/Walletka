package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.CodeIssue
import cz.poqet.walletka.domain.issueWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodeIssueTest {

    @Test
    fun ean13ChecksItsCheckDigit() {
        assertNull(CodeFormat.EAN_13.issueWith("4006381333931"))
        assertEquals(CodeIssue.CHECK_DIGIT, CodeFormat.EAN_13.issueWith("4006381333932"))
        // Without the check digit, which the symbology adds.
        assertNull(CodeFormat.EAN_13.issueWith("400638133393"))
    }

    @Test
    fun retailCodesHoldDigitsOfTheRightLength() {
        assertEquals(CodeIssue.DIGITS_ONLY, CodeFormat.EAN_8.issueWith("1234ABCD"))
        assertEquals(CodeIssue.LENGTH, CodeFormat.EAN_13.issueWith("12345"))
        assertNull(CodeFormat.UPC_A.issueWith("036000291452"))
        assertEquals(CodeIssue.LENGTH, CodeFormat.ITF.issueWith("12345"))
    }

    @Test
    fun alphanumericSymbologiesRejectWhatTheyCantEncode() {
        assertNull(CodeFormat.CODE_39.issueWith("GYM-004211"))
        assertEquals(CodeIssue.CHARACTERS, CodeFormat.CODE_39.issueWith("gym-004211"))
        assertEquals(CodeIssue.CHARACTERS, CodeFormat.CODE_128.issueWith("Karta č. 5"))
        assertNull(CodeFormat.QR.issueWith("Karta č. 5"))
    }

    @Test
    fun blankIsNotAnIssue() {
        assertNull(CodeFormat.EAN_13.issueWith("  "))
    }
}

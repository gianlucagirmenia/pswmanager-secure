package com.durdencorp.pswmanager.service.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.durdencorp.pswmanager.dto.PasswordEntryForm;
import com.durdencorp.pswmanager.service.MasterPasswordEncryption;
import com.durdencorp.pswmanager.service.PasswordEntryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordImportServiceTest {
    @Test
    void encryptedImportRestoresOriginalPasswordAndOverwritesExistingEntry() {
        PasswordEntryService passwords = mock(PasswordEntryService.class);
        MasterPasswordEncryption encryption = mock(MasterPasswordEncryption.class);
        PasswordImportService service = new PasswordImportService();
        ReflectionTestUtils.setField(service, "passwordService", passwords);
        ReflectionTestUtils.setField(service, "encryption", encryption);

        String json = "{\"exportDate\":\"2025-01-01T00:00:00\",\"totalEntries\":1,\"entries\":["
                + "{\"title\":\"Mail\",\"username\":\"user\",\"encryptedPassword\":\"entry-cipher\","
                + "\"url\":null,\"notes\":null,\"category\":\"Email\",\"createdAt\":null,\"updatedAt\":null}]}";
        when(encryption.decrypt("backup-cipher")).thenReturn(json);
        when(encryption.decrypt("entry-cipher")).thenReturn("real-password");
        when(passwords.existsByTitleAndUsername("Mail", "user")).thenReturn(true);
        when(passwords.findIdByTitleAndUsername("Mail", "user")).thenReturn(42L);

        service.importEncryptedJson("backup-cipher", true);

        ArgumentCaptor<PasswordEntryForm> captor = ArgumentCaptor.forClass(PasswordEntryForm.class);
        verify(passwords).save(captor.capture());
        assertEquals("real-password", captor.getValue().getPlainPassword());
        assertEquals(42L, captor.getValue().getId());
    }
}

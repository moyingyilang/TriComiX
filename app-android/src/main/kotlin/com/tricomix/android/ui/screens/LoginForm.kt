package com.tricomix.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tricomix.core.source.Capability

/**
 * 登录表单（从 `MainActivity` 抽出，理由与其它屏相同：界面代码不堆在脚手架里）。
 *
 * 状态由调用方持有（用户名/密码/登录结果与忙碌标志都是 hoisted 参数），
 * 这样凭据的存放与提交策略仍由上层决定 —— 界面本身不接触持久化。
 *
 * 登录按钮按 [Capability.LOGIN] 显隐：源不支持登录时按钮直接不可用，
 * 而不是让使用者点了才发现。
 */
@Composable
fun LoginForm(
    accountLabel: String,
    user: String,
    password: String,
    busy: Boolean,
    canLogin: Boolean,
    status: String,
    onUserChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = user,
            onValueChange = onUserChange,
            label = { Text(accountLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("密码（不会保存）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && canLogin, onClick = onLogin) { Text("登录") }
            Button(enabled = !busy, onClick = onLogout) { Text("登出") }
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

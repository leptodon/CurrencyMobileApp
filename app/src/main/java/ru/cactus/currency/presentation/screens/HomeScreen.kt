package ru.cactus.currency.presentation.screens

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import ru.cactus.currency.presentation.component.CurrencyCard

@Composable
internal fun HomeScreen(
    symbols: Map<String, String>,
    rates: Map<String, String>,
    onItemClick: (String) -> Unit
) {
    if (rates.isNotEmpty()) {
        LazyColumn {
            symbols.forEach { (symbol, name) ->
                item {
                    rates[symbol]?.let {
                        CurrencyCard(
                            currency = name,
                            symbol = symbol,
                            rate = it,
                            onItemClick = onItemClick
                        )
                    }
                }
            }
        }
    }
}
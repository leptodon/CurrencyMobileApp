package ru.cactus.currency.domain

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.cactus.currency.data.entity.local.Symbols
import ru.cactus.currency.presentation.entity.CardContent
import ru.cactus.currency.presentation.entity.StateUI
import ru.cactus.currency.domain.repository.DatabaseRepository
import ru.cactus.currency.domain.repository.NetworkRepository
import ru.cactus.currency.utils.toCorrectFloat
import ru.cactus.currency.utils.toImgSymbol
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyUseCases @Inject constructor(
    private val localRepository: DatabaseRepository,
    private val networkRepository: NetworkRepository,
    private val scope: CoroutineScope
) {

    private val _stateUiData: MutableStateFlow<StateUI> = MutableStateFlow(StateUI())
    val stateUiData: StateFlow<StateUI> = _stateUiData

    suspend fun getSymbols() {
        if (stateUiData.value.symbolsMap.isEmpty()) {
            if (localRepository.isSymbolsExists()) {
                with(localRepository.getAllSymbols()) {
                    _stateUiData.update { state ->
                        state.copy(
                            symbolsList = this.sortedBy { it.name },
                            symbolsMap = this.associate { it.symbol to it.name }
                        )
                    }
                }
            } else {
                with(networkRepository.getSymbols()) {
                    if (isSuccessful) {
                        body()?.let { data ->
                            _stateUiData.update {
                                it.copy(
                                    symbolsList = data.symbols.map { (symbol, name) ->
                                        Symbols(
                                            symbol = symbol,
                                            name = name
                                        )
                                    }.sortedBy { sort -> sort.name },
                                    symbolsMap = data.symbols
                                )
                            }
                        }

                        body()?.symbols?.map { Symbols(it.key, it.value) }
                            ?.let { localRepository.insertSymbols(it) }
                    }
                }
            }
        }
    }

    suspend fun getRates(baseCurrency: String) {
        val repo = networkRepository.getCurrenciesRates(baseCurrency)
        if (repo.isSuccessful) {
            _stateUiData.update {
                it.copy(
                    ratesMap = repo.body()?.rates ?: emptyMap(),
                )
            }
        }
    }

    suspend fun updateContent(isHomeContentList: Boolean) {
        val list = mutableListOf(CardContent())

        if (isHomeContentList) {
            list.clear()
            stateUiData.value.symbolsList.forEach { (symbol, name) ->
                list.add(
                    CardContent(
                        symbol = symbol,
                        currencyName = name,
                        rate = stateUiData.value.ratesMap[symbol] ?: "0.0",
                        isFavorite = localRepository.isFavoriteSymbol(symbol),
                        currencySymbolImg = symbol.toImgSymbol()
                    )
                )
            }
            _stateUiData.update { it.copy(contentList = list) }
        } else {
            list.clear()
            localRepository.getAllFavorites().forEach { (symbol, name) ->
                list.add(
                    CardContent(
                        symbol = symbol,
                        currencyName = name,
                        rate = stateUiData.value.ratesMap[symbol] ?: "0.0",
                        currencySymbolImg = symbol.toImgSymbol()
                    )
                )
            }
            _stateUiData.update { it.copy(contentFavoriteList = list) }
        }
    }

    suspend fun changeFavorite(symbols: Symbols) {
        val isFavorite = localRepository.isFavoriteSymbol(symbols.symbol)
        if (isFavorite) {
            localRepository.changeFavoriteStatus(
                symbols.copy(isFavorite = false)
            )
        } else {
            localRepository.changeFavoriteStatus(
                symbols.copy(isFavorite = true)
            )
        }
        updateContent(true)
    }

    fun setCurrency(symbols: String) {
        scope.launch {
            getRates(symbols)
        }
    }

    fun filter(isAlphabet: Boolean) {
        if (isAlphabet) {
            _stateUiData.update {
                it.copy(contentList = it.contentList.sortedBy { s -> s.symbol },
                    contentFavoriteList = it.contentFavoriteList.sortedBy { v -> v.symbol })
            }
        } else {
            _stateUiData.update { state ->
                state.copy(
                    contentList = state.contentList.sortedBy {
                        it.rate.toCorrectFloat()
                    },
                    contentFavoriteList = state.contentFavoriteList.sortedBy { it.rate.toCorrectFloat() })
            }
        }
    }
}
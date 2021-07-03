/*
 * Copyright 2011-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.data;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Currency;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.Logging;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.GenericUtils;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    /*public static final String KEY_CURRENCY_CODE = "currency_code";
    private static final String KEY_RATE_COIN = "rate_coin";
    private static final String KEY_RATE_FIAT = "rate_fiat";
    private static final String KEY_SOURCE = "source";

    public static final String QUERY_PARAM_Q = "q";
    private static final String QUERY_PARAM_OFFLINE = "offline";

    private Configuration config;
    private String userAgent;

    @Nullable
    private Map<String, ExchangeRate> exchangeRates = null;
    private long lastUpdated = 0;

    private static final HttpUrl BITCOINAVERAGE_URL = HttpUrl
            .parse("https://apiv2.bitcoinaverage.com/indices/global/ticker/short?crypto=BTC");
    private static final String BITCOINAVERAGE_SOURCE = "BitcoinAverage.com";

    private static final long UPDATE_FREQ_MS = 10 * DateUtils.MINUTE_IN_MILLIS;

    private static final Logger log = LoggerFactory.getLogger(ExchangeRatesProvider.class);

    @Override
    public boolean onCreate() {
        if (!Constants.ENABLE_EXCHANGE_RATES)
            return false;

        final Stopwatch watch = Stopwatch.createStarted();

        final Context context = getContext();
        Logging.init(context.getFilesDir());
        final WalletApplication application = (WalletApplication) context.getApplicationContext();
        this.config = application.getConfiguration();
        this.userAgent = WalletApplication.httpUserAgent(application.packageInfo().versionName);

        final ExchangeRate cachedExchangeRate = config.getCachedExchangeRate();
        if (cachedExchangeRate != null) {
            exchangeRates = new TreeMap<>();
            exchangeRates.put(cachedExchangeRate.getCurrencyCode(), cachedExchangeRate);
        }

        watch.stop();
        log.info("{}.onCreate() took {}", getClass().getSimpleName(), watch);
        return true;
    }

    public static Uri contentUri(final String packageName, final boolean offline) {
        final Uri.Builder uri = Uri.parse("content://" + packageName + '.' + "exchange_rates").buildUpon();
        if (offline)
            uri.appendQueryParameter(QUERY_PARAM_OFFLINE, "1");
        return uri.build();
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs,
            final String sortOrder) {
        final long now = System.currentTimeMillis();

        final boolean offline = uri.getQueryParameter(QUERY_PARAM_OFFLINE) != null;

        if (!offline && (lastUpdated == 0 || now - lastUpdated > UPDATE_FREQ_MS)) {
            Map<String, ExchangeRate> newExchangeRates = null;
            if (newExchangeRates == null)
                newExchangeRates = requestExchangeRates();

            if (newExchangeRates == null)
                newExchangeRates = requestExchangeRatesBackup();


            if (newExchangeRates != null) {
                exchangeRates = newExchangeRates;
                lastUpdated = now;

                final ExchangeRate exchangeRateToCache = bestExchangeRate(config.getExchangeCurrencyCode());
                if (exchangeRateToCache != null)
                    config.setCachedExchangeRate(exchangeRateToCache);
            }
        }

        if (exchangeRates == null)
            return null;

        final MatrixCursor cursor = new MatrixCursor(
                new String[] { BaseColumns._ID, KEY_CURRENCY_CODE, KEY_RATE_COIN, KEY_RATE_FIAT, KEY_SOURCE });

        if (selection == null) {
            for (final Map.Entry<String, ExchangeRate> entry : exchangeRates.entrySet()) {
                final ExchangeRate exchangeRate = entry.getValue();
                final org.bitcoinj.utils.ExchangeRate rate = exchangeRate.rate;
                final String currencyCode = exchangeRate.getCurrencyCode();
                cursor.newRow().add(currencyCode.hashCode()).add(currencyCode).add(rate.coin.value).add(rate.fiat.value)
                        .add(exchangeRate.source);
            }
        } else if (selection.equals(QUERY_PARAM_Q)) {
            final String selectionArg = selectionArgs[0].toLowerCase(Locale.US);
            for (final Map.Entry<String, ExchangeRate> entry : exchangeRates.entrySet()) {
                final ExchangeRate exchangeRate = entry.getValue();
                final org.bitcoinj.utils.ExchangeRate rate = exchangeRate.rate;
                final String currencyCode = exchangeRate.getCurrencyCode();
                final String currencySymbol = GenericUtils.currencySymbol(currencyCode);
                if (currencyCode.toLowerCase(Locale.US).contains(selectionArg)
                        || currencySymbol.toLowerCase(Locale.US).contains(selectionArg))
                    cursor.newRow().add(currencyCode.hashCode()).add(currencyCode).add(rate.coin.value)
                            .add(rate.fiat.value).add(exchangeRate.source);
            }
        } else if (selection.equals(KEY_CURRENCY_CODE)) {
            final String selectionArg = selectionArgs[0];
            final ExchangeRate exchangeRate = bestExchangeRate(selectionArg);
            if (exchangeRate != null) {
                final org.bitcoinj.utils.ExchangeRate rate = exchangeRate.rate;
                final String currencyCode = exchangeRate.getCurrencyCode();
                cursor.newRow().add(currencyCode.hashCode()).add(currencyCode).add(rate.coin.value).add(rate.fiat.value)
                        .add(exchangeRate.source);
            }
        }

        return cursor;
    }

    private ExchangeRate bestExchangeRate(final String currencyCode) {
        ExchangeRate rate = currencyCode != null ? exchangeRates.get(currencyCode) : null;
        if (rate != null)
            return rate;

        final String defaultCode = defaultCurrencyCode();
        rate = defaultCode != null ? exchangeRates.get(defaultCode) : null;

        if (rate != null)
            return rate;

        return exchangeRates.get(Constants.DEFAULT_EXCHANGE_CURRENCY);
    }

    private String defaultCurrencyCode() {
        try {
            return Currency.getInstance(Locale.getDefault()).getCurrencyCode();
        } catch (final IllegalArgumentException x) {
            return null;
        }
    }

    public static ExchangeRate getExchangeRate(final Cursor cursor) {
        final String currencyCode = cursor
                .getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_CURRENCY_CODE));
        final Coin rateCoin = Coin
                .valueOf(cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_COIN)));
        final Fiat rateFiat = Fiat.valueOf(currencyCode,
                cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_FIAT)));
        final String source = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_SOURCE));

        return new ExchangeRate(new org.bitcoinj.utils.ExchangeRate(rateCoin, rateFiat), source);
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(final Uri uri) {
        throw new UnsupportedOperationException();
    }

    private Map<String, ExchangeRate> requestExchangeRates() {
        final Stopwatch watch = Stopwatch.createStarted();

        Double GRSPerBTC = requestExchangeRatesForGRS(bitcoinAverage);
        if(GRSPerBTC == null)
            GRSPerBTC = requestExchangeRatesForGRS(bittrex);
        if(GRSPerBTC == null)
            GRSPerBTC = requestExchangeRatesForGRS(binance);

        final Request.Builder request = new Request.Builder();
        request.url(BITCOINAVERAGE_URL);
        final Headers.Builder headers = new Headers.Builder();
        headers.add("User-Agent", userAgent);
        request.headers(headers.build());

        final Builder httpClientBuilder = Constants.HTTP_CLIENT.newBuilder();
        httpClientBuilder.connectionSpecs(Arrays.asList(ConnectionSpec.RESTRICTED_TLS));
        final Call call = httpClientBuilder.build().newCall(request.build());
        try {
            final Response response = call.execute();
            if (response.isSuccessful()) {
                final String content = response.body().string();
                final JSONObject head = new JSONObject(content);
                final Map<String, ExchangeRate> rates = new TreeMap<>();

                for (final Iterator<String> i = head.keys(); i.hasNext();) {
                    final String currencyCode = i.next();
                    if (currencyCode.startsWith("BTC")) {
                        final String fiatCurrencyCode = currencyCode.substring(3);
                        if (!fiatCurrencyCode.equals(MonetaryFormat.CODE_BTC)
                                && !fiatCurrencyCode.equals(MonetaryFormat.CODE_MBTC)
                                && !fiatCurrencyCode.equals(MonetaryFormat.CODE_UBTC)) {
                            final JSONObject exchangeRate = head.getJSONObject(currencyCode);
                            try {
                                Double _rate = GRSPerBTC *exchangeRate.getDouble("last");
                                final Fiat rate = parseFiatInexact(fiatCurrencyCode, _rate.toString());
                                //final Fiat rate = parseFiatInexact(fiatCurrencyCode, exchangeRate.getString("last"));
                                if (rate.signum() > 0)
                                    rates.put(fiatCurrencyCode, new ExchangeRate(
                                            new org.bitcoinj.utils.ExchangeRate(rate), BITCOINAVERAGE_SOURCE));
                            } catch (final IllegalArgumentException x) {
                                log.warn("problem fetching {} exchange rate from {}: {}", currencyCode,
                                        BITCOINAVERAGE_URL, x.getMessage());
                            }
                        }
                    }
                }

                watch.stop();
                log.info("fetched exchange rates from {}, {} chars, took {}", BITCOINAVERAGE_URL, content.length(),
                        watch);

                return rates;
            } else {
                log.warn("http status {} when fetching exchange rates from {}", response.code(), BITCOINAVERAGE_URL);
            }
        } catch (final Exception x) {
            log.warn("problem fetching exchange rates from " + BITCOINAVERAGE_URL, x);
        }

        return null;
    }

    // backport from bitcoinj 0.15
    private static Fiat parseFiatInexact(final String currencyCode, final String str) {
        final long val = new BigDecimal(str).movePointRight(Fiat.SMALLEST_UNIT_EXPONENT).longValue();
        return Fiat.valueOf(currencyCode, val);
    }

    private abstract class GroestlcoinExchangeRateProvider {
        String url;
        GroestlcoinExchangeRateProvider(String url) {
            this.url = url;
        }

        public abstract Double parseResponse(JSONObject response) throws JSONException;
    }

    private String BITTREX_URL = "https://bittrex.com/api/v1.1/public/getticker?market=btc-grs";

    GroestlcoinExchangeRateProvider bittrex = new GroestlcoinExchangeRateProvider(BITTREX_URL) {
        @Override
        public Double parseResponse(JSONObject response) throws JSONException {
            String result = response.getString("success");
            if(result.equals("true"))
            {
                JSONObject dataObject = response.getJSONObject("result");
                return dataObject.getDouble("Last");
            }
            return null;
        }
    };

    private static String BITCOIN_AVERAGE_GRSBTC_URL = "https://apiv2.bitcoinaverage.com/indices/crypto/ticker/GRSBTC";

    GroestlcoinExchangeRateProvider bitcoinAverage = new GroestlcoinExchangeRateProvider(BITCOIN_AVERAGE_GRSBTC_URL) {
        @Override
        public Double parseResponse(JSONObject response) throws JSONException {
            return response.getDouble("last");
        }
    };

    public static final String BINANCE_EXCHANGE_URL = "https://api.binance.com/api/v1/ticker/price?symbol=GRSBTC";

    GroestlcoinExchangeRateProvider binance = new GroestlcoinExchangeRateProvider(BINANCE_EXCHANGE_URL) {
        @Override
        public Double parseResponse(JSONObject response) throws JSONException {
            return response.getDouble("price");
        }
    };

    private Double requestExchangeRatesForGRS(GroestlcoinExchangeRateProvider provider) {
        final Stopwatch watch = Stopwatch.createStarted();

        final Request.Builder request = new Request.Builder();
        request.url(provider.url);
        request.header("User-Agent", userAgent);

        final Call call = Constants.HTTP_CLIENT.newCall(request.build());
        try {
            final Response response = call.execute();
            if (response.isSuccessful()) {
                final String content = response.body().string();

                final JSONObject head = new JSONObject(content.toString());
                Double averageTrade = provider.parseResponse(head);
                watch.stop();
                log.info("fetched exchange rates from {}, {} chars, took {}", BITCOINAVERAGE_URL, content.length(),
                        watch);
                return averageTrade;
            } else {
                log.warn("http status {} when fetching exchange rates from {}", response.code(), BITCOINAVERAGE_URL);
            }
        } catch (final Exception x) {
            log.warn("problem fetching exchange rates from " + BITTREX_URL, x);
        }

        return null;
    }

    private static String COINGECKO_URL = "https://api.coingecko.com/api/v3/coins/groestlcoin?localization=false&community_data=false&developer_data=false&sparkline=false";
    private static final String COINGECKO_SOURCE = "CoinGecko.com";

    private Map<String, ExchangeRate> requestExchangeRatesBackup() {
        final Stopwatch watch = Stopwatch.createStarted();

        final Request.Builder request = new Request.Builder();
        request.url(COINGECKO_URL);
        request.header("User-Agent", userAgent);

        final Builder httpClientBuilder = Constants.HTTP_CLIENT.newBuilder();
        httpClientBuilder.connectionSpecs(Arrays.asList(ConnectionSpec.RESTRICTED_TLS));
        final Call call = httpClientBuilder.build().newCall(request.build());
        try {
            final Response response = call.execute();
            if (response.isSuccessful()) {
                final String content = response.body().string();
                final JSONObject head = new JSONObject(content);
                final Map<String, ExchangeRate> rates = new TreeMap<String, ExchangeRate>();

                //check for correct information
                if(head.getString("id").equals("groestlcoin")) {
                    JSONObject marketData = head.getJSONObject("market_data");
                    JSONObject currentPrice = marketData.getJSONObject("current_price");

                    for (final Iterator<String> i = currentPrice.keys(); i.hasNext(); ) {
                        final String currencyCode = i.next();
                        final String fiatCurrencyCode = currencyCode.toUpperCase();
                        if (!fiatCurrencyCode.equals(MonetaryFormat.CODE_BTC)
                                && !fiatCurrencyCode.equals(MonetaryFormat.CODE_MBTC)
                                && !fiatCurrencyCode.equals(MonetaryFormat.CODE_UBTC)) {
                            final String exchangeRate = currentPrice.getString(currencyCode);//head.getJSONObject(currencyCode);
                            try {
                                //Double _rate = GRSPerBTC * exchangeRate.getDouble("last");
                                //final Fiat rate = parseFiatInexact(fiatCurrencyCode, _rate.toString());
                                final Fiat rate = parseFiatInexact(fiatCurrencyCode, exchangeRate);
                                if (rate.signum() > 0)
                                    rates.put(fiatCurrencyCode, new ExchangeRate(
                                            new org.bitcoinj.utils.ExchangeRate(rate), COINGECKO_SOURCE));
                            } catch (final IllegalArgumentException x) {
                                log.warn("problem fetching {} exchange rate from {}: {}", currencyCode,
                                        COINGECKO_URL, x.getMessage());
                            }
                        }
                    }

                    watch.stop();
                    log.info("fetched exchange rates from {}, {} chars, took {}", COINGECKO_URL, content.length(),
                            watch);

                    return rates;
                } else {
                    log.warn("wrong or missing id when fetching exchange rates from {}", response.code(), COINGECKO_URL);
                }
            } else {
                log.warn("http status {} when fetching exchange rates from {}", response.code(), COINGECKO_URL);
            }
        } catch (final Exception x) {
            log.warn("problem fetching exchange rates from " + COINGECKO_URL, x);
        }

        return null;
    }*/
}

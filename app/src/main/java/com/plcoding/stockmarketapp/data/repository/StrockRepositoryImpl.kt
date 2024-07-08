package com.plcoding.stockmarketapp.data.repository

import android.media.MediaParser.InputReader
import coil.network.HttpException
import com.opencsv.CSVReader
import com.plcoding.stockmarketapp.data.csv.CVSParser
import com.plcoding.stockmarketapp.data.csv.CompanyListingsParser
import com.plcoding.stockmarketapp.data.local.StockDatabase
import com.plcoding.stockmarketapp.data.mapper.toCompanyInfo
import com.plcoding.stockmarketapp.data.mapper.toCompanyListing
import com.plcoding.stockmarketapp.data.mapper.toCompanyListingEntity
import com.plcoding.stockmarketapp.data.remote.StockApi
import com.plcoding.stockmarketapp.domain.model.CompanyInfo
import com.plcoding.stockmarketapp.domain.model.CompanyListing
import com.plcoding.stockmarketapp.domain.model.IntradayInfo
import com.plcoding.stockmarketapp.domain.repository.StockRepository
import com.plcoding.stockmarketapp.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.IOException
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StrockRepositoryImpl @Inject constructor(
   private val api: StockApi,
   private val db: StockDatabase,
   private val companyListingsParser: CVSParser<CompanyListing>,
   private val intradayInfoParser: CVSParser<IntradayInfo>
) : StockRepository {

    private var dao = db.dao

    override suspend fun getCompanyListings(

        fetchFromRemote: Boolean,
        query: String

    ): Flow<Resource<List<CompanyListing>>> {
        return flow {

            emit(Resource.Loanding(true))
            val localListings = dao.searchCompanyListing(query)
            emit(Resource.Success(
                data = localListings.map { it.toCompanyListing() }
            ))

            val isDbEmpty = localListings.isEmpty() && query.isBlank()
            val shouldJustLoadFromCache = !isDbEmpty && !fetchFromRemote
            if (shouldJustLoadFromCache) {
                emit(Resource.Loanding(false))

                return@flow
            }

            val remoteListings = try {
                val response = api.getListing()
                companyListingsParser.parse(response.byteStream())

            } catch (e: IOException) {
                e.printStackTrace()
                emit(Resource.Error("No se puedo cargar los datos"))
                null
            } catch (e: HttpException) {
                e.printStackTrace()
                emit(Resource.Error("No se puedo cargar los datos"))
                null
            }

            remoteListings?.let { listings ->

                dao.clearCompanyListings()
                dao.insertCompanyListings(
                    listings.map { it.toCompanyListingEntity() }
                )

                emit(
                    Resource.Success(
                        data = dao
                            .searchCompanyListing("")
                            .map { it.toCompanyListing() })
                )
                emit(Resource.Loanding(false))
            }
        }
    }

    override suspend fun getIntradayInfo(symbol: String): Resource<List<IntradayInfo>> {
        return try {
            val respnse= api.getIntradayInfo(symbol)
            val result= intradayInfoParser.parse(respnse.byteStream())
            Resource.Success(result)

        }catch (e: IOException){
            e.printStackTrace()
            Resource.Error(
                message = "No se puedo cagar la informacion del dia"
            )
        }catch (e : HttpException){
            e.printStackTrace()
            Resource.Error(
                message = "No se puedo cagar la informacion del dia"
            )
        }


    }

    override suspend fun getCompanyInfo(symbol: String): Resource<CompanyInfo> {
        return try {
           val result= api.getCompanyInfo(symbol)
            Resource.Success(result.toCompanyInfo())

        }catch (e: IOException){
            e.printStackTrace()
            Resource.Error(
                message = "No se puedo cagar la informacion de la compania"
            )
        }catch (e : HttpException){
            e.printStackTrace()
            Resource.Error(
                message = "No se puedo cagar la informacion de la compania"
            )
        }
    }

}
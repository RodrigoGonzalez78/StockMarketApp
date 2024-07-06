package com.plcoding.stockmarketapp.data.repository

import android.media.MediaParser.InputReader
import coil.network.HttpException
import com.opencsv.CSVReader
import com.plcoding.stockmarketapp.data.csv.CVSParser
import com.plcoding.stockmarketapp.data.csv.CompanyListingsParser
import com.plcoding.stockmarketapp.data.local.StockDatabase
import com.plcoding.stockmarketapp.data.mapper.toCompanyListing
import com.plcoding.stockmarketapp.data.mapper.toCompanyListingEntity
import com.plcoding.stockmarketapp.data.remote.StockApi
import com.plcoding.stockmarketapp.domain.model.CompanyListing
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
    val api: StockApi,
    val db: StockDatabase,
    val companyListingsParser: CVSParser<CompanyListing>
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

}
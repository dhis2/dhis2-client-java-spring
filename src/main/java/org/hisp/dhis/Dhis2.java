package org.hisp.dhis;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.hisp.dhis.model.Category;
import org.hisp.dhis.model.CategoryCombo;
import org.hisp.dhis.model.CategoryOption;
import org.hisp.dhis.model.CategoryOptionGroupSet;
import org.hisp.dhis.model.DataElement;
import org.hisp.dhis.model.DataElementGroup;
import org.hisp.dhis.model.DataElementGroupSet;
import org.hisp.dhis.model.Dimension;
import org.hisp.dhis.model.IdentifiableObject;
import org.hisp.dhis.model.MetadataObjects;
import org.hisp.dhis.model.OrgUnit;
import org.hisp.dhis.model.OrgUnitGroup;
import org.hisp.dhis.model.OrgUnitGroupSet;
import org.hisp.dhis.model.OrgUnitLevel;
import org.hisp.dhis.model.PeriodType;
import org.hisp.dhis.model.Program;
import org.hisp.dhis.model.SystemSettings;
import org.hisp.dhis.model.TableHook;
import org.hisp.dhis.model.datavalueset.DataValueSet;
import org.hisp.dhis.model.datavalueset.DataValueSetImportOptions;
import org.hisp.dhis.query.Query;
import org.hisp.dhis.query.analytics.AnalyticsQuery;
import org.hisp.dhis.request.orgunit.OrgUnitMergeRequest;
import org.hisp.dhis.request.orgunit.OrgUnitSplitRequest;
import org.hisp.dhis.response.Dhis2ClientException;
import org.hisp.dhis.response.HttpStatus;
import org.hisp.dhis.response.Response;
import org.hisp.dhis.response.datavalueset.DataValueSetResponse;
import org.hisp.dhis.response.job.JobCategory;
import org.hisp.dhis.response.job.JobNotification;
import org.hisp.dhis.response.metadata.ObjectResponse;
import org.hisp.dhis.util.HttpUtils;

/**
 * DHIS 2 API client for HTTP requests and responses. Request and
 * response bodies are in JSON format.
 *
 * @author Lars Helge Overland
 */
public class Dhis2
    extends BaseDhis2
{
    public Dhis2( Dhis2Config config )
    {
        super( config );
    }

    // -------------------------------------------------------------------------
    // Generic methods
    // -------------------------------------------------------------------------

    /**
     * Checks the status of the DHIS 2 instance. Returns various status codes describing
     * the status:
     *
     * <ul>
     * <li>{@link HttpStatus#OK} if instance is available and authentication is successful.</li>
     * <li>{@link HttpStatus#UNAUTHORIZED} if the username and password combination is not valid.</li>
     * <li>{@link HttpStatus#NOT_FOUND} if the URL is not pointing to a DHIS 2 instance or the
     *     DHIS 2 instance is not available.</li>
     * </ul>
     *
     * @return the HTTP status code of the response.
     */
    public HttpStatus getStatus()
    {
        try
        {
            URI url = HttpUtils.build( config.getResolvedUriBuilder()
                .appendPath( "system" )
                .appendPath( "info" ) );

            HttpGet request = withBasicAuth( new HttpGet( url ) );
            CloseableHttpResponse response = httpClient.execute( request );
            int statusCode = response.getCode();
            return HttpStatus.valueOf( statusCode );
        }
        catch ( IOException ex )
        {
            // Return status code in case of unexpected exception of type HttpResponseException

            if ( ex instanceof HttpResponseException )
            {
                int statusCode = ((HttpResponseException) ex).getStatusCode();
                return HttpStatus.valueOf( statusCode );
            }

            throw new UncheckedIOException( ex );
        }
    }

    /**
     * Returns the URL of the DHIS 2 configuration.
     *
     * @return the URL of the DHIS 2 configuration.
     */
    public String getDhis2Url()
    {
        return config.getUrl();
    }

    /**
     * Returns the username of the DHIS 2 configuration.
     *
     * @return the username of the DHIS 2 configuration.
     */
    public String getDhis2Username()
    {
        return config.getUsername();
    }

    /**
     * Saves a metadata object using HTTP POST.
     *
     * @param path the URL path relative to the API end point.
     * @param object the object to save.
     * @return a {@link ObjectResponse} holding information about the operation.
     * @throws Dhis2ClientException if the save operation failed due to client side error.
     */
    public ObjectResponse saveMetadataObject( String path, IdentifiableObject object )
    {
        URI url = config.getResolvedUrl( path );

        return executeJsonPostPutRequest( new HttpPost( url ), object, ObjectResponse.class );
    }

    /**
     * Saves or updates metadata objects.
     *
     * @param objects the {@link MetadataObjects}.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveMetadataObjects( MetadataObjects objects )
    {
        URI url = config.getResolvedUrl( "metadata" );

        return executeJsonPostPutRequest( new HttpPost( url ), objects, ObjectResponse.class );
    }

    /**
     * Updates an object using HTTP PUT.
     *
     * @param path the URL path relative to the API end point.
     * @param object the object to save.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateMetadataObject( String path, IdentifiableObject object )
    {
        URI url = config.getResolvedUrl( path );

        return executeJsonPostPutRequest( new HttpPut( url ), object, ObjectResponse.class );
    }

    /**
     * Updates an object using HTTP DELETE.
     *
     * @param path the URL path relative to the API end point.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeMetadataObject( String path )
    {
        URI url = config.getResolvedUrl( path );

        return executeJsonPostPutRequest( new HttpDelete( url ), null, ObjectResponse.class );
    }

    /**
     * Retrieves an object using HTTP GET.
     *
     * @param path the URL path relative to the API end point.
     * @param klass the class type of the object.
     * @param <T> type.
     * @return the object.
     */
    public <T> T getObject( String path, Class<T> klass )
    {
        return getObjectFromUrl( config.getResolvedUrl( path ), klass );
    }

    /**
     * Indicates whether an object exists at the given URL path
     * using HTTP HEAD.
     *
     * @param path the URL path relative to the API end point.
     * @return true if the object exists.
     */
    public boolean objectExists( String path )
    {
        URI url = config.getResolvedUrl( path );

        HttpHead request = withBasicAuth( new HttpHead( url ) );

        try ( CloseableHttpResponse response = httpClient.execute( request ) )
        {
            return HttpStatus.OK.value() == response.getCode();
        }
        catch ( IOException ex )
        {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Org unit
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link OrgUnit}.
     *
     * @param orgUnit the object to save.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveOrgUnit( OrgUnit orgUnit )
    {
        return saveMetadataObject( "organisationUnits", orgUnit );
    }

    /**
     * Saves or updates the list of {@link OrgUnit}.
     *
     * @param orgUnits the list of {@link OrgUnit}.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveOrUpdateOrgUnits( List<OrgUnit> orgUnits )
    {
        return saveMetadataObjects( new MetadataObjects().setOrganisationUnits( orgUnits ) );
    }

    /**
     * Updates a {@link OrgUnit}.
     *
     * @param orgUnit the object to update.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateOrgUnit( OrgUnit orgUnit )
    {
        return updateMetadataObject( String.format( "organisationUnits/%s", orgUnit.getId() ), orgUnit );
    }

    /**
     * Removes a {@link OrgUnit}.
     *
     * @param id the identifier of the object to remove.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeOrgUnit( String id )
    {
        return removeMetadataObject( String.format( "organisationUnits/%s", id ) );
    }

    /**
     * Retrieves an {@link OrgUnit}.
     *
     * @param id the object identifier.
     * @return the {@link OrgUnit}.
     */
    public OrgUnit getOrgUnit( String id )
    {
        String fields = NAME_FIELDS + ",path,level";

        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnits" )
            .appendPath( id )
            .addParameter( "fields", String.format( "%s,parent[%s]", fields, fields ) ), Query.instance(), OrgUnit.class );
    }

    /**
     * Retrieves a list of {@link OrgUnit}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link OrgUnit}.
     */
    public List<OrgUnit> getOrgUnits( Query query )
    {
        String fields = NAME_FIELDS + ",path,level";

        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnits" )
            .addParameter( "fields", String.format( "%s,parent[%s]", fields, fields ) ), query, MetadataObjects.class )
            .getOrganisationUnits();
    }

    // -------------------------------------------------------------------------
    // Org unit merge and split
    // -------------------------------------------------------------------------

    /**
     * Performs an org unit split operation.
     *
     * @param request the {@link OrgUnitSplitRequest}.
     * @return a {@link Response} holding information about the operation.
     */
    public Response splitOrgUnit( OrgUnitSplitRequest request )
    {
        URI url = config.getResolvedUrl( "organisationUnits/split" );

        return executeJsonPostPutRequest( new HttpPost( url ), request, Response.class );
    }

    /**
     * Performs an org unit merge operation.
     *
     * @param request the {@link OrgUnitMergeRequest request}.
     * @return a {@link Response} holding information about the operation.
     */
    public Response mergeOrgUnits( OrgUnitMergeRequest request )
    {
        URI url = config.getResolvedUrl( "organisationUnits/merge" );

        return executeJsonPostPutRequest( new HttpPost( url ), request, Response.class );
    }

    // -------------------------------------------------------------------------
    // Org unit group
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link OrgUnitGroup}.
     *
     * @param orgUnitGroup the object to save.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveOrgUnitGroup( OrgUnitGroup orgUnitGroup )
    {
        return saveMetadataObject( "organisationUnitGroups", orgUnitGroup );
    }

    /**
     * Updates a {@link OrgUnitGroup}.
     *
     * @param orgUnitGroup the object to update.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateOrgUnitGroup( OrgUnitGroup orgUnitGroup )
    {
        return updateMetadataObject( String.format( "organisationUnitGroups/%s", orgUnitGroup.getId() ), orgUnitGroup );
    }

    /**
     * Removes a {@link OrgUnitGroup}.
     *
     * @param id the identifier of the object to remove.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeOrgUnitGroup( String id )
    {
        return removeMetadataObject( String.format( "organisationUnitGroups/%s", id ) );
    }

    /**
     * Retrieves an {@link OrgUnitGroup}.
     *
     * @param id the object identifier.
     * @return the {@link OrgUnitGroup}.
     */
    public OrgUnitGroup getOrgUnitGroup( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitGroups" )
            .appendPath( id )
            .addParameter( "fields", NAME_FIELDS ), Query.instance(), OrgUnitGroup.class );
    }

    /**
     * Retrieves a list of {@link OrgUnitGroup}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link OrgUnitGroup}.
     */
    public List<OrgUnitGroup> getOrgUnitGroups( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitGroups" )
            .addParameter( "fields", NAME_FIELDS ), query, MetadataObjects.class )
            .getOrganisationUnitGroups();
    }

    // -------------------------------------------------------------------------
    // Org unit group set
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link OrgUnitGroupSet}.
     *
     * @param orgUnitGroupSet the object to save.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveOrgUnitGroupSet( OrgUnitGroupSet orgUnitGroupSet )
    {
        return saveMetadataObject( "organisationUnitGroupSets", orgUnitGroupSet );
    }

    /**
     * Updates a {@link OrgUnitGroupSet}.
     *
     * @param orgUnitGroupSet the object to update.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateOrgUnitGroupSet( OrgUnitGroupSet orgUnitGroupSet )
    {
        return updateMetadataObject( String.format( "organisationUnitGroupSets/%s", orgUnitGroupSet.getId() ), orgUnitGroupSet );
    }

    /**
     * Removes a {@link OrgUnitGroupSet}.
     *
     * @param id the identifier of the object to remove.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeOrgUnitGroupSet( String id )
    {
        return removeMetadataObject( String.format( "organisationUnitGroupSets/%s", id ) );
    }

    /**
     * Retrieves an {@link OrgUnitGroupSet}.
     *
     * @param id the object identifier.
     * @return the {@link OrgUnitGroupSet}.
     */
    public OrgUnitGroupSet getOrgUnitGroupSet( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitGroupSets" )
            .appendPath( id )
            .addParameter( "fields", String.format( "%s,organisationUnitGroups[%s]", NAME_FIELDS, NAME_FIELDS ) ), Query.instance(), OrgUnitGroupSet.class );
    }

    /**
     * Retrieves a list of {@link OrgUnitGroupSet}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link OrgUnitGroupSet}.
     */
    public List<OrgUnitGroupSet> getOrgUnitGroupSets( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitGroupSets" )
            .addParameter( "fields", String.format( "%s,organisationUnitGroups[%s]", NAME_FIELDS, NAME_FIELDS ) ), query, MetadataObjects.class )
            .getOrganisationUnitGroupSets();
    }

    // -------------------------------------------------------------------------
    // Org unit level
    // -------------------------------------------------------------------------

    /**
     * Retrieves an {@link OrgUnitLevel}.
     *
     * @param id the object identifier.
     * @return the {@link OrgUnitLevel}.
     */
    public OrgUnitLevel getOrgUnitLevel( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitLevels" )
            .appendPath( id )
            .addParameter( "fields", String.format( "%s,level", ID_FIELDS ) ), Query.instance(), OrgUnitLevel.class );
    }

    /**
     * Retrieves a list of {@link OrgUnitLevel}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link OrgUnitLevel}.
     */
    public List<OrgUnitLevel> getOrgUnitLevels( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitLevels" )
            .addParameter( "fields", String.format( "%s,level", ID_FIELDS ) ), query, MetadataObjects.class )
            .getOrganisationUnitLevels();
    }

    /**
     * Retrieves a list of "filled" {@link OrgUnitLevel}, meaning
     * any gaps in the persisted levels will be inserted by generated
     * levels.
     *
     * @return a list of {@link OrgUnitLevel}.
     */
    public List<OrgUnitLevel> getFilledOrgUnitLevels()
    {
        // Using array, DHIS 2 should have used a wrapper entity for the response

        return asList( getObject( config.getResolvedUriBuilder()
            .appendPath( "filledOrganisationUnitLevels" ), Query.instance(), OrgUnitLevel[].class ) );
    }

    // -------------------------------------------------------------------------
    // Category option
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link CategoryOption}.
     *
     * @param categoryOption the object to save.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveCategoryOption( CategoryOption categoryOption )
    {
        return saveMetadataObject( "categoryOptions", categoryOption );
    }

    /**
     * Updates a {@link CategoryOption}.
     *
     * @param categoryOption the object to update.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateCategoryOption( CategoryOption categoryOption )
    {
        return updateMetadataObject( String.format( "categoryOptions/%s", categoryOption.getId() ), categoryOption );
    }

    /**
     * Removes a {@link CategoryOption}.
     *
     * @param id the identifier of the object to remove.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeCategoryOption( String id )
    {
        return removeMetadataObject( String.format( "categoryOptions/%s", id ) );
    }

    /**
     * Retrieves an {@link CategoryOption}.
     *
     * @param id the object identifier.
     * @return the {@link CategoryOption}.
     */
    public CategoryOption getCategoryOption( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryOptions" )
            .appendPath( id )
            .addParameter( "fields", CATEGORY_OPTION_FIELDS ), Query.instance(), CategoryOption.class );
    }

    /**
     * Retrieves a list of {@link CategoryOption}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link CategoryOption}.
     */
    public List<Category> getCategoryOptions( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryOptions" )
            .addParameter( "fields", CATEGORY_OPTION_FIELDS ), query, MetadataObjects.class )
            .getCategories();
    }

    // -------------------------------------------------------------------------
    // Category
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link Category}.
     *
     * @param category the object to save.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveCategory( Category category )
    {
        return saveMetadataObject( "categories", category );
    }

    /**
     * Updates a {@link Category}.
     *
     * @param categoryOption the object to update.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateCategory( CategoryOption categoryOption )
    {
        return updateMetadataObject( String.format( "categories/%s", categoryOption.getId() ), categoryOption );
    }

    /**
     * Removes a {@link Category}.
     *
     * @param id the identifier of the object to remove.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeCategory( String id )
    {
        return removeMetadataObject( String.format( "categories/%s", id ) );
    }

    /**
     * Retrieves an {@link Category}.
     *
     * @param id the object identifier.
     * @return the {@link Category}.
     */
    public Category getCategory( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categories" )
            .appendPath( id )
            .addParameter( "fields", CATEGORY_FIELDS ), Query.instance(), Category.class );
    }

    /**
     * Retrieves a list of {@link Category}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link Category}.
     */
    public List<Category> getCategories( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categories" )
            .addParameter( "fields", CATEGORY_FIELDS ), query, MetadataObjects.class )
            .getCategories();
    }

    // -------------------------------------------------------------------------
    // Category combo
    // -------------------------------------------------------------------------

    /**
     * Retrieves an {@link CategoryCombo}.
     *
     * @param id the object identifier.
     * @return the {@link CategoryCombo}.
     */
    public CategoryCombo getCategoryCombo( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryCombos" )
            .appendPath( id )
            .addParameter( "fields", NAME_FIELDS ), Query.instance(), CategoryCombo.class );
    }

    /**
     * Retrieves a list of {@link CategoryCombo}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link CategoryCombo}.
     */
    public List<CategoryCombo> getCategoryCombos( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryCombos" )
            .addParameter( "fields", NAME_FIELDS ), query, MetadataObjects.class )
            .getCategoryCombos();
    }

    // -------------------------------------------------------------------------
    // Data element
    // -------------------------------------------------------------------------

    /**
     * Retrieves an {@link DataElement}.
     *
     * @param id the object identifier.
     * @return the {@link DataElement}.
     */
    public DataElement getDataElement( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElements" )
            .appendPath( id )
            .addParameter( "fields", DATA_ELEMENT_FIELDS ), Query.instance(), DataElement.class );
    }

    /**
     * Retrieves a list of {@link DataElement}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link DataElement}.
     */
    public List<DataElement> getDataElements( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElements" )
            .addParameter( "fields", DATA_ELEMENT_FIELDS ), query, MetadataObjects.class )
            .getDataElements();
    }

    // -------------------------------------------------------------------------
    // Data element group
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link DataElement}.
     *
     * @param dataElementGroup the object to save.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveDataElementGroup( DataElementGroup dataElementGroup )
    {
        return saveMetadataObject( "dataElementGroups", dataElementGroup );
    }

    /**
     * Updates a {@link DataElementGroup}.
     *
     * @param dataElementGroup the object to update.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateDataElementGroup( DataElementGroup dataElementGroup )
    {
        return updateMetadataObject( String.format( "dataElementGroups/%s", dataElementGroup.getId() ), dataElementGroup );
    }

    /**
     * Removes a {@link DataElementGroup}.
     *
     * @param id the identifier of the object to remove.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeDataElementGroup( String id )
    {
        return removeMetadataObject( String.format( "dataElementGroups/%s", id ) );
    }

    /**
     * Retrieves an {@link DataElementGroup}.
     *
     * @param id the object identifier.
     * @return the {@link DataElementGroup}.
     */
    public DataElementGroup getDataElementGroup( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElementGroups" )
            .appendPath( id )
            .addParameter( "fields", NAME_FIELDS ), Query.instance(), DataElementGroup.class );
    }

    /**
     * Retrieves a list of {@link DataElementGroup}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link DataElementGroup}.
     */
    public List<DataElementGroup> getDataElementGroups( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElementGroups" )
            .addParameter( "fields", NAME_FIELDS ), query, MetadataObjects.class )
            .getDataElementGroups();
    }

    // -------------------------------------------------------------------------
    // Data element group set
    // -------------------------------------------------------------------------

    /**
     * Retrieves an {@link DataElementGroupSet}.
     *
     * @param id the object identifier.
     * @return the {@link DataElementGroupSet}.
     */
    public DataElementGroupSet getDataElementGroupSet( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElementGroupSets" )
            .appendPath( id )
            .addParameter( "fields", NAME_FIELDS ), Query.instance(), DataElementGroupSet.class );
    }

    /**
     * Retrieves a list of {@link DataElementGroupSet}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link DataElementGroupSet}.
     */
    public List<DataElementGroupSet> getDataElementGroupSets( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElementGroupSets" )
            .addParameter( "fields", NAME_FIELDS ), query, MetadataObjects.class )
            .getDataElementGroupSets();
    }

    // -------------------------------------------------------------------------
    // Program
    // -------------------------------------------------------------------------

    /**
     * Retrieves a {@link Program}.
     *
     * @param id the object identifier.
     * @return the {@link Program}.
     */
    public Program getProgram( String id )
    {
        String fieldsParam = String.format(
            "%1$s,programType,categoryCombo[%1$s,categories[%2$s]]," +
            "programStages[%1$s,programStageDataElements[%1$s,dataElement[%3$s]]]," +
            "programTrackedEntityAttributes[id,code,name,trackedEntityAttribute[%4$s]]",
            NAME_FIELDS, CATEGORY_FIELDS, DATA_ELEMENT_FIELDS, TE_ATTRIBUTE_FIELDS );

        return getObject( config.getResolvedUriBuilder()
            .appendPath( "programs" )
            .appendPath( id )
            .addParameter( "fields", fieldsParam ), Query.instance(), Program.class );
    }

    /**
     * Retrieves a list of {@link Program}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link Program}.
     */
    public List<Program> getPrograms( Query query )
    {
        String fieldsParam = query.isExpandAssociations() ?
            String.format(
                "%1$s,programType,categoryCombo[%1$s,categories[%2$s]]," +
                "programStages[%1$s,programStageDataElements[%1$s,dataElement[%3$s]]]," +
                "programTrackedEntityAttributes[id,code,name,trackedEntityAttribute[%4$s]]",
            NAME_FIELDS, CATEGORY_FIELDS, DATA_ELEMENT_FIELDS, TE_ATTRIBUTE_FIELDS ) :
            String.format(
                "%1$s,programType,categoryCombo[%1$s],programStages[%1$s],programTrackedEntityAttributes[%1$s]",
                NAME_FIELDS );

        return getObject( config.getResolvedUriBuilder()
            .appendPath( "programs" )
            .addParameter( "fields", fieldsParam ), query, MetadataObjects.class )
            .getPrograms();
    }

    // -------------------------------------------------------------------------
    // Category option group set
    // -------------------------------------------------------------------------

    /**
     * Retrieves an {@link CategoryOptionGroupSet}.
     *
     * @param id the object identifier.
     * @return the {@link CategoryOptionGroupSet}.
     */
    public CategoryOptionGroupSet getCategoryOptionGroupSet( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryOptionGroupSets" )
            .appendPath( id )
            .addParameter( "fields", NAME_FIELDS ), Query.instance(), CategoryOptionGroupSet.class );
    }

    /**
     * Retrieves a list of {@link CategoryOptionGroupSet}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link CategoryOptionGroupSet}.
     */
    public List<CategoryOptionGroupSet> getCategoryOptionGroupSets( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryOptionGroupSets" )
            .addParameter( "fields", NAME_FIELDS ), query, MetadataObjects.class )
            .getCategoryOptionGroupSets();
    }

    // -------------------------------------------------------------------------
    // Table hook
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link TableHook}.
     *
     * @param tableHook the object to save.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveTableHook( TableHook tableHook )
    {
        return saveMetadataObject( "analyticsTableHooks", tableHook );
    }

    /**
     * Updates a {@link TableHook}.
     *
     * @param tableHook the object to update.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateTableHook( TableHook tableHook )
    {
        return updateMetadataObject( String.format( "analyticsTableHooks/%s", tableHook.getId() ), tableHook );
    }

    /**
     * Removes a {@link TableHook}.
     *
     * @param id the identifier of the object to remove.
     * @return a {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeTableHook( String id )
    {
        return removeMetadataObject( String.format( "analyticsTableHooks/%s", id ) );
    }

    /**
     * Retrieves an {@link TableHook}.
     *
     * @param id the identifier of the table hook.
     * @return the {@link TableHook}.
     */
    public TableHook getTableHook( String id )
    {
        return getObject( "analyticsTableHooks", id, TableHook.class );
    }

    /**
     * Retrieves a list of {@link TableHook}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link TableHook}.
     */
    public List<TableHook> getTableHooks( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "analyticsTableHooks" )
            .addParameter( "fields", ID_FIELDS ), query, MetadataObjects.class )
            .getAnalyticsTableHooks();
    }

    // -------------------------------------------------------------------------
    // Dimension
    // -------------------------------------------------------------------------

    /**
     * Retrieves a {@link Dimension}.
     *
     * @param id the identifier of the dimension.
     * @return the {@link Dimension}.
     */
    public Dimension getDimension( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dimensions" )
            .appendPath( id )
            .addParameter( "fields", String.format( "%s,dimensionType", ID_FIELDS ) ), Query.instance(), Dimension.class );
    }

    /**
     * Retrieves a list of {@link Dimension}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link Dimension}.
     */
    public List<Dimension> getDimensions( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dimensions" )
            .addParameter( "fields", String.format( "%s,dimensionType", ID_FIELDS ) ), query, MetadataObjects.class )
            .getDimensions();
    }

    // -------------------------------------------------------------------------
    // Period type
    // -------------------------------------------------------------------------

    /**
     * Retrieves a list of {@link PeriodType}.
     *
     * @param query the {@link Query}.
     * @return a list of {@link PeriodType}.
     */
    public List<PeriodType> getPeriodTypes( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "periodTypes" )
            .addParameter( "fields", "frequencyOrder,name,isoDuration,isoFormat" ), query, MetadataObjects.class )
            .getPeriodTypes();
    }

    // -------------------------------------------------------------------------
    // System settings
    // -------------------------------------------------------------------------

    /**
     * Retrieves {@link SystemSettings}.
     *
     * @return system settings.
     */
    public SystemSettings getSystemSettings()
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "systemSettings" ), Query.instance(), SystemSettings.class );
    }

    // -------------------------------------------------------------------------
    // Data value set
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link DataValueSet}.
     *
     * @param dataValueSet the {@link DataValueSet} to save.
     * @param options the {@link DataValueSetImportOptions}.
     * @return a {@link DataValueSetResponse} holding information about the operation.
     * @throws IOException if the save process failed.
     */
    public DataValueSetResponse saveDataValueSet( DataValueSet dataValueSet, DataValueSetImportOptions options )
        throws IOException
    {
        URI url = getDataValueSetImportQuery( config.getResolvedUriBuilder()
            .appendPath( "dataValueSets" ), options );

        HttpPost request = getPostRequest( url, new StringEntity( toJsonString( dataValueSet ), StandardCharsets.UTF_8 ) );

        Dhis2AsyncRequest asyncRequest = new Dhis2AsyncRequest( config, httpClient, objectMapper );

        return asyncRequest.post( request, DataValueSetResponse.class );
    }

    /**
     * Saves a data value set payload in JSON format represented by the given file.
     *
     * @param file the file representing the data value set JSON payload.
     * @param options the {@link DataValueSetImportOptions}.
     * @return a {@link DataValueSetResponse} holding information about the operation.
     * @throws IOException if the save process failed.
     */
    public DataValueSetResponse saveDataValueSet( File file, DataValueSetImportOptions options )
        throws IOException
    {
        URI url = getDataValueSetImportQuery( config.getResolvedUriBuilder()
            .appendPath( "dataValueSets" ), options );

        HttpPost request = getPostRequest( url, new FileEntity( file, ContentType.APPLICATION_JSON ) );

        Dhis2AsyncRequest asyncRequest = new Dhis2AsyncRequest( config, httpClient, objectMapper );

        return asyncRequest.post( request, DataValueSetResponse.class );
    }

    // -------------------------------------------------------------------------
    // Analytics data value set
    // -------------------------------------------------------------------------

    /**
     * Retrieves a {@link DataValueSet}.
     *
     * @param query the {@link AnalyticsQuery}.
     * @return a {@link DataValueSet}.
     */
    public DataValueSet getAnalyticsDataValueSet( AnalyticsQuery query )
    {
        return getAnalyticsResponse( config.getResolvedUriBuilder()
            .appendPath( "analytics" )
            .appendPath( "dataValueSet.json" ), query, DataValueSet.class );
    }

    /**
     * Retrieves a {@link DataValueSet} and writes it to the given file.
     *
     * @param query the {@link AnalyticsQuery}.
     * @param file the {@link File}.
     * @throws IOException if writing the response to file failed.
     */
    public void writeAnalyticsDataValueSet( AnalyticsQuery query, File file )
        throws IOException
    {
        URI url = getAnalyticsQuery( config.getResolvedUriBuilder()
            .appendPath( "analytics" )
            .appendPath( "dataValueSet.json" ), query );

        CloseableHttpResponse response = getJsonHttpResponse( url );

        writeToFile( response, file );
    }

    // -------------------------------------------------------------------------
    // Job notifications
    // -------------------------------------------------------------------------

    /**
     * Retrieves a list of {@link JobNotification}.
     *
     * @param category the {@link JobCategory}.
     * @param id the job identifier.
     * @return a list of {@link JobNotification}.
     */
    public List<JobNotification> getJobNotifications( JobCategory category, String id )
    {
        JobNotification[] response = getObject( config.getResolvedUriBuilder()
            .appendPath( "system" )
            .appendPath( "tasks" )
            .appendPath( category.name() )
            .appendPath( id ), Query.instance(), JobNotification[].class );

        return new ArrayList<>( Arrays.asList( response ) );
    }
}
